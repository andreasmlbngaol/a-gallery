package id.andreasmbngaol.agallery.presentation.viewer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Pause
import com.adamglin.phosphoricons.bold.Play
import com.adamglin.phosphoricons.bold.SpeakerHigh
import com.adamglin.phosphoricons.bold.SpeakerSlash
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.drawsBackdrop
import id.andreasmbngaol.agallery.core.ui.usesBlur
import id.andreasmbngaol.agallery.core.ui.usesLens
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.media3.common.MediaItem as ExoMediaItem

private val GlassBlurRadius = 4.dp
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
private const val GlassTintAlpha = 0.3f
private const val FrostedHazeAlpha = 0.4f
private const val FrostedFallbackAlpha = 0.55f
private const val SolidFallbackAlpha = 0.95f

private const val HoldSpeedMultiplier = 2f
private const val SeekStepMs = 5_000L

/**
 * Video audio state that applies WHILE THE APP IS OPEN (session, in-memory).
 *
 * Defaults to mute each time the app is freshly opened. Once the user unmutes/mutes
 * via the button (or changes the system volume -> auto unmute), this new value
 * becomes the default for subsequent videos in the same session. Because it is
 * just an in-memory object (not DataStore), it RESETS back to mute when the app
 * process is killed.
 */
object VideoPlaybackPrefs {
    var isMuted by mutableStateOf(true)
}

/**
 * Full-screen video player for the viewer.
 *
 * Features:
 * - **Autoplay** as soon as the page is active ([isActive] = true), looped
 *   ([Player.REPEAT_MODE_ONE]). Preload pages (left/right) stay paused.
 * - **MUTE by default** (volume 0) — the user can unmute via the far-right button.
 * - **Bottom controls** in a **liquid glass** container (Kyant, API 33+;
 *   frosted-solid fallback below it), left->right order:
 *   `[play/pause] [current time] [====slider====] [total time] mute`.
 * - **TikTok-style gestures**: single tap = show/hide controls; double tap CENTER =
 *   play/pause; double tap LEFT = back 5 seconds (capped at 0); double tap RIGHT =
 *   forward 5 seconds; hold the LEFT or RIGHT side = 2x fast-forward.
 *
 * ## Slider bug fixes
 * 1. **Jump back**: pendingSeekMs locks the DISPLAYED position to the target
 *    until the player actually catches up (diff <= 350ms), then resumes normal polling.
 * 2. **Video plays while still holding**: as soon as scrubbing starts, playback is
 *    PAUSED (state saved in wasPlayingBeforeScrub) & only resumes when the finger lifts.
 * 3. **Seek missing to a far keyframe** (e.g. 5s jumps back to 0): live scrubbing
 *    uses CLOSEST_SYNC (fast), but on release uses SeekParameters.EXACT so it
 *    lands precisely on the chosen second.
 *
 * ## Liquid glass over the video
 * PlayerView is forced to `surface_type=texture_view` (see res/layout/
 * view_video_player.xml) so the video frames render in the view hierarchy and
 * can be captured as a [layerBackdrop] to be refracted by the glass panel.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerContent(
    uri: String,
    isActive: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    style: ComponentStyle,
    modifier: Modifier = Modifier,
    actionsSlot: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            prepare()
        }
    }

    val isMuted = VideoPlaybackPrefs.isMuted
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableFloatStateOf(0f) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var wasPlayingBeforeHold by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }
    var controlsVisibleBeforeHold by remember { mutableStateOf(false) }
    var skipFeedbackVisible by remember { mutableStateOf(false) }
    var skipFeedbackForward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var skipFeedbackJob by remember { mutableStateOf<Job?>(null) }
    val latestControlsVisible by rememberUpdatedState(controlsVisible)

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(context) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current != lastVolume) {
                    lastVolume = current
                    if (VideoPlaybackPrefs.isMuted) VideoPlaybackPrefs.isMuted = false
                }
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val pending = pendingSeekMs
            when {
                isScrubbing -> Unit
                pending != null -> {
                    if (abs(pos - pending) <= 350L) {
                        pendingSeekMs = null
                        positionMs = pos
                    }
                }
                else -> positionMs = pos
            }
            if (durationMs <= 0L) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(200.milliseconds)
        }
    }

    val displayPositionMs = when {
        isScrubbing && durationMs > 0L -> (scrubTarget * durationMs).toLong()
        pendingSeekMs != null -> pendingSeekMs ?: 0L
        else -> positionMs
    }
    val sliderValue = when {
        isScrubbing -> scrubTarget
        pendingSeekMs != null && durationMs > 0L ->
            ((pendingSeekMs ?: 0L).toFloat() / durationMs).coerceIn(0f, 1f)
        durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Box(modifier = modifier) {
        val videoModifier =
            if (style.drawsBackdrop()) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                val tempParent = FrameLayout(ctx)
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_video_player, tempParent, false) as PlayerView
                view.player = exoPlayer
                view
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val widthPx = size.width.toFloat()
                        val leftZone = down.position.x < widthPx / 3f
                        val rightZone = down.position.x > widthPx * 2f / 3f

                        val firstUp = withTimeoutOrNull(
                            viewConfiguration.longPressTimeoutMillis,
                        ) { waitForUpOrCancellation() }

                        if (firstUp == null) {
                            val holdActive = leftZone || rightZone
                            if (holdActive) {
                                wasPlayingBeforeHold = exoPlayer.playWhenReady
                                speedBeforeHold = exoPlayer.playbackParameters.speed
                                exoPlayer.setPlaybackSpeed(HoldSpeedMultiplier)
                                exoPlayer.playWhenReady = true
                                isFastForwarding = true
                                controlsVisibleBeforeHold = latestControlsVisible
                                if (latestControlsVisible) onToggleControls()
                            }
                            waitForUpOrCancellation()
                            if (holdActive) {
                                exoPlayer.setPlaybackSpeed(speedBeforeHold)
                                exoPlayer.playWhenReady = wasPlayingBeforeHold
                                isFastForwarding = false
                                if (controlsVisibleBeforeHold && !latestControlsVisible) {
                                    onToggleControls()
                                }
                            }
                        } else {
                            val secondDown = withTimeoutOrNull(
                                viewConfiguration.doubleTapTimeoutMillis,
                            ) { awaitFirstDown(requireUnconsumed = false) }
                            if (secondDown == null) {
                                onToggleControls()
                            } else {
                                withTimeoutOrNull(
                                    viewConfiguration.longPressTimeoutMillis,
                                ) { waitForUpOrCancellation() }
                                when {
                                    leftZone -> {
                                        val target = (exoPlayer.currentPosition - SeekStepMs)
                                            .coerceAtLeast(0L)
                                        exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                        exoPlayer.seekTo(target)
                                        skipFeedbackForward = false
                                        skipFeedbackVisible = true
                                        skipFeedbackJob?.cancel()
                                        skipFeedbackJob = scope.launch {
                                            delay(600.milliseconds)
                                            skipFeedbackVisible = false
                                        }
                                    }
                                    rightZone -> {
                                        val dur = exoPlayer.duration
                                        val max = if (dur > 0L) dur else Long.MAX_VALUE
                                        val target = (exoPlayer.currentPosition + SeekStepMs)
                                            .coerceAtMost(max)
                                        exoPlayer.setSeekParameters(SeekParameters.EXACT)
                                        exoPlayer.seekTo(target)
                                        skipFeedbackForward = true
                                        skipFeedbackVisible = true
                                        skipFeedbackJob?.cancel()
                                        skipFeedbackJob = scope.launch {
                                            delay(600.milliseconds)
                                            skipFeedbackVisible = false
                                        }
                                    }
                                    else -> {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        )

        AnimatedVisibility(
            visible = isFastForwarding,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.video_speed_2x),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        AnimatedVisibility(
            visible = skipFeedbackVisible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(
                    if (skipFeedbackForward) Alignment.CenterEnd
                    else Alignment.CenterStart,
                )
                .padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (skipFeedbackForward) stringResource(R.string.video_skip_forward) else stringResource(R.string.video_skip_backward),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val glassTint =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = if (style == ComponentStyle.FROSTED) FrostedHazeAlpha else GlassTintAlpha,
                )
            val fallbackTint =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = if (style == ComponentStyle.FROSTED) FrostedFallbackAlpha else SolidFallbackAlpha,
                )
            val contentColor = MaterialTheme.colorScheme.onSurface
            val timeTextStyle =
                MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")

            val containerModifier =
                if (style.drawsBackdrop()) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(16.dp) },
                        effects = {
                            vibrancy()
                            if (style.usesBlur()) {
                                blur(GlassBlurRadius.toPx())
                            }
                            if (style.usesLens()) {
                                lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                            }
                        },
                        onDrawSurface = { drawRect(glassTint) },
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(16.dp)).background(fallbackTint)
                }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .then(containerModifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            PhosphorIcons.Bold.Pause
                        } else {
                            PhosphorIcons.Bold.Play
                        },
                        contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                        tint = contentColor,
                    )
                }

                Text(
                    text = formatTime(displayPositionMs),
                    color = contentColor,
                    style = timeTextStyle,
                )

                Slider(
                    modifier = Modifier.weight(1f),
                    value = sliderValue,
                    onValueChange = { value ->
                        if (!isScrubbing) {
                            wasPlayingBeforeScrub = exoPlayer.playWhenReady
                            exoPlayer.playWhenReady = false
                        }
                        isScrubbing = true
                        scrubTarget = value
                        if (durationMs > 0L) {
                            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            exoPlayer.seekTo((value * durationMs).toLong())
                        }
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val target = (scrubTarget * durationMs).toLong()
                            pendingSeekMs = target
                            exoPlayer.setSeekParameters(SeekParameters.EXACT)
                            exoPlayer.seekTo(target)
                        }
                        isScrubbing = false
                        exoPlayer.playWhenReady = wasPlayingBeforeScrub
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = contentColor,
                        activeTrackColor = contentColor,
                        inactiveTrackColor = contentColor.copy(alpha = 0.3f),
                    ),
                )

                Text(
                    text = formatTime(durationMs),
                    color = contentColor,
                    style = timeTextStyle,
                )

                IconButton(onClick = { VideoPlaybackPrefs.isMuted = !VideoPlaybackPrefs.isMuted }) {
                    Icon(
                        imageVector = if (isMuted) {
                            PhosphorIcons.Bold.SpeakerSlash
                        } else {
                            PhosphorIcons.Bold.SpeakerHigh
                        },
                        contentDescription = if (isMuted) stringResource(R.string.action_unmute) else stringResource(R.string.action_mute),
                        tint = contentColor,
                    )
                }
                }
                if (actionsSlot != null) {
                    Spacer(Modifier.height(4.dp))
                    actionsSlot()
                }
            }
        }
    }
}

/** Format milliseconds -> "m:ss" (or "h:mm:ss" when >= 1 hour). */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
