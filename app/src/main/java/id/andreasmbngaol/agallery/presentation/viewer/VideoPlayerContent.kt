package id.andreasmbngaol.agallery.presentation.viewer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
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
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.media3.common.MediaItem as ExoMediaItem

// ---- Tuning liquid glass (samain dengan floating nav bar) ----
private val GlassBlurRadius = 4.dp
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
private const val GlassTintAlpha = 0.3f
// Veil "haze" FROSTED (drawBackdrop TANPA blur/lens) -> sedikit lebih pekat.
private const val FrostedHazeAlpha = 0.4f
// Non-glass: FROSTED translusen (masih berkesan kaca), SOLID hampir opaque.
private const val FrostedFallbackAlpha = 0.55f
private const val SolidFallbackAlpha = 0.95f

// Gesture tahan-geser (hold) ala TikTok: percepat 2x (kiri & kanan sama-sama maju).
private const val HoldSpeedMultiplier = 2f
// Loncatan double-tap sisi kiri/kanan: mundur/maju 5 detik.
private const val SeekStepMs = 5_000L

/**
 * State audio video yang berlaku SELAMA APP DIBUKA (session, in-memory).
 *
 * Default mute tiap kali app baru dibuka. Begitu user unmute/mute lewat tombol
 * (atau ubah volume sistem -> auto unmute), nilai baru ini jadi default untuk
 * video-video berikutnya di sesi yang sama. Karena cuma object di memori (bukan
 * DataStore), nilainya RESET ke mute lagi saat proses app dimatikan.
 */
object VideoPlaybackPrefs {
    var isMuted by mutableStateOf(true)
}

/**
 * Pemutar video full-screen untuk viewer.
 *
 * Fitur:
 * - **Autoplay** begitu halaman aktif ([isActive] = true), di-loop
 *   ([Player.REPEAT_MODE_ONE]). Halaman preload (kiri/kanan) tetap pause.
 * - **Default MUTE** (volume 0) — user bisa unmute lewat tombol paling kanan.
 * - **Kontrol bawah** dalam container **liquid glass** (Kyant, API 33+;
 *   fallback frosted solid di bawahnya), urutan kiri->kanan:
 *   `[play/pause] [current time] [====slider====] [total time] [mute]`.
 * - **Gesture** ala TikTok: 1x tap = show/hide kontrol; 2x tap TENGAH =
 *   play/pause; 2x tap KIRI = mundur 5 detik (cap 0); 2x tap KANAN = maju
 *   5 detik; tahan sisi KIRI atau KANAN = maju cepat 2x.
 *
 * ## Fix bug slider
 * 1. **Loncat balik**: [pendingSeekMs] mengunci posisi yang DITAMPILKAN ke target
 *    sampai player benar-benar nyusul (selisih <= 350ms), baru polling normal.
 * 2. **Video jalan saat masih menahan**: begitu scrub mulai, playback di-PAUSE
 *    (status disimpan di [wasPlayingBeforeScrub]) & baru lanjut saat jari lepas.
 * 3. **Seek meleset ke keyframe jauh** (mis. 5s malah balik ke 0): scrub live
 *    pakai CLOSEST_SYNC (cepat), tapi saat dilepas pakai SeekParameters.EXACT
 *    supaya benar-benar mendarat presisi di detik yang dipilih.
 *
 * ## Liquid glass di atas video
 * PlayerView dipaksa `surface_type=texture_view` (lihat res/layout/
 * view_video_player.xml) supaya frame video ikut ter-render di view hierarchy
 * dan bisa di-capture sebagai [layerBackdrop] untuk dibiaskan panel kaca.
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

    // Satu ExoPlayer per video. remember(uri) -> instance baru kalau uri ganti.
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // default mute
            // Scrub cepat: loncat ke keyframe terdekat supaya preview frame
            // muncul instan saat slider digeser (bukan nunggu decode presisi).
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            prepare()
        }
    }

    // Mute pakai state SESSION (VideoPlaybackPrefs) supaya konsisten antar video
    // selama app dibuka; lihat KDoc object-nya.
    val isMuted = VideoPlaybackPrefs.isMuted
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    // true selama user menyeret slider -> jangan override dari player.
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableFloatStateOf(0f) }
    // Target seek yang "dikunci" sampai player nyusul (anti loncat-balik).
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    // Status main sebelum scrub -> dipulihkan setelah jari lepas (bug 1).
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    // Gesture tahan (hold) maju cepat 2x ala TikTok (kiri & kanan sama).
    var isFastForwarding by remember { mutableStateOf(false) }
    var wasPlayingBeforeHold by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }
    // Status tampil kontrol/topbar sebelum hold -> dipulihkan sesuai aslinya.
    var controlsVisibleBeforeHold by remember { mutableStateOf(false) }
    // Umpan balik double-tap loncat. `visible` mengatur animasi; `forward`
    // menyimpan arah & DIPERTAHANKAN selama animasi keluar biar tak flicker
    // ke sisi lain saat state di-null-kan.
    var skipFeedbackVisible by remember { mutableStateOf(false) }
    var skipFeedbackForward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var skipFeedbackJob by remember { mutableStateOf<Job?>(null) }
    // pointerInput pakai key Unit -> closure bisa basi; rememberUpdatedState
    // memastikan gesture selalu baca controlsVisible terbaru.
    val latestControlsVisible by rememberUpdatedState(controlsVisible)

    // Autoplay hanya di halaman aktif; pindah halaman -> pause.
    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }

    // Volume ikut state mute.
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Auto-unmute kalau user mengubah volume media sistem (tekan tombol volume).
    // Pakai ContentObserver di Settings.System; bandingkan volume STREAM_MUSIC
    // untuk memfilter perubahan setting lain. Terdaftar hanya selama composable
    // video ada, dilepas saat keluar.
    DisposableEffect(context) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current != lastVolume) {
                    lastVolume = current
                    // Perubahan volume media -> user mau dengar -> unmute.
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

    // Polling posisi tiap 200ms. Kalau ada pendingSeek, tahan tampilan di target
    // sampai player nyusul biar slider tidak loncat balik.
    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val pending = pendingSeekMs
            when {
                isScrubbing -> Unit // ikut jari, jangan update
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

    // Posisi & nilai slider yang DITAMPILKAN (prioritas: scrub > pendingSeek > real).
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
        // --- Surface video (jadi sumber backdrop kalau glass didukung) ---
        val videoModifier =
            if (style.drawsBackdrop()) {
                Modifier.fillMaxSize().layerBackdrop(backdrop)
            } else {
                Modifier.fillMaxSize()
            }
        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                // Inflate dari XML supaya surface_type=texture_view & controller off.
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_video_player, null) as PlayerView
                view.player = exoPlayer
                view
            },
        )

        // --- Gesture area (tap / double-tap / hold ala TikTok) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val widthPx = size.width.toFloat()
                        val leftZone = down.position.x < widthPx / 3f
                        val rightZone = down.position.x > widthPx * 2f / 3f

                        // Fase 1: HOLD (jari tetap turun lewati long-press) vs TAP.
                        val firstUp = withTimeoutOrNull(
                            viewConfiguration.longPressTimeoutMillis,
                        ) { waitForUpOrCancellation() }

                        if (firstUp == null) {
                            // ===== HOLD ===== kiri & kanan sama-sama maju cepat 2x.
                            val holdActive = leftZone || rightZone
                            if (holdActive) {
                                wasPlayingBeforeHold = exoPlayer.playWhenReady
                                speedBeforeHold = exoPlayer.playbackParameters.speed
                                exoPlayer.setPlaybackSpeed(HoldSpeedMultiplier)
                                exoPlayer.playWhenReady = true
                                isFastForwarding = true
                                // Sembunyikan kontrol/topbar/actions sementara,
                                // TAPI hanya kalau memang lagi tampil.
                                controlsVisibleBeforeHold = latestControlsVisible
                                if (latestControlsVisible) onToggleControls()
                            }
                            // Tunggu jari diangkat -> pulihkan.
                            waitForUpOrCancellation()
                            if (holdActive) {
                                exoPlayer.setPlaybackSpeed(speedBeforeHold)
                                exoPlayer.playWhenReady = wasPlayingBeforeHold
                                isFastForwarding = false
                                // Pulihkan kontrol hanya kalau tadinya tampil
                                // (state awal hide -> tetap hide).
                                if (controlsVisibleBeforeHold && !latestControlsVisible) {
                                    onToggleControls()
                                }
                            }
                        } else {
                            // ===== TAP: cek tap kedua utk double-tap =====
                            val secondDown = withTimeoutOrNull(
                                viewConfiguration.doubleTapTimeoutMillis,
                            ) { awaitFirstDown(requireUnconsumed = false) }
                            if (secondDown == null) {
                                // Single tap -> show/hide kontrol.
                                onToggleControls()
                            } else {
                                // Double tap (konsumsi up kedua) -> aksi per zona.
                                withTimeoutOrNull(
                                    viewConfiguration.longPressTimeoutMillis,
                                ) { waitForUpOrCancellation() }
                                when {
                                    leftZone -> {
                                        // Mundur 5 detik, cap ke 0.
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
                                        // Maju 5 detik, cap ke durasi (kalau diketahui).
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
                                        // Tengah -> play/pause.
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

        // --- Indikator hold maju 2x di tengah atas ---
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

        // --- Umpan balik double-tap loncat 5 detik (kiri/kanan) ---
        // Arah dibaca dari skipFeedbackForward (bukan nilai yang di-null-kan),
        // jadi selama animasi keluar posisinya tetap di sisi yang benar.
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

        // --- Baris kontrol (glass) di bawah ---
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
            // Warna konten ikut tema (light/dark). Container kaca pakai tint
            // surfaceContainerHighest, jadi onSurface = kontras di kedua mode
            // (bukan putih hardcoded yang hilang di light mode).
            val contentColor = MaterialTheme.colorScheme.onSurface
            // tnum = tabular figures: semua digit lebar sama, jadi lebar teks
            // waktu stabil walau angka berubah (0:20 -> 0:21) -> slider tak geser.
            val timeTextStyle =
                MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")

            // Container kaca: drawBackdrop (API 33+) atau clip+background (fallback).
            val containerModifier =
                if (style.drawsBackdrop()) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(16.dp) },
                        effects = {
                            vibrancy()
                            // GLASS = blur + lens. FROSTED = keduanya off -> veil haze saja.
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
                // --- Control center (di ATAS) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                // Play / pause (paling kiri)
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
                            // Awal scrub: ingat status main lalu PAUSE supaya video
                            // tidak jalan selama jari masih menahan (bug 1).
                            wasPlayingBeforeScrub = exoPlayer.playWhenReady
                            exoPlayer.playWhenReady = false
                        }
                        isScrubbing = true
                        scrubTarget = value
                        // Live-preview cepat ke keyframe terdekat.
                        if (durationMs > 0L) {
                            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            exoPlayer.seekTo((value * durationMs).toLong())
                        }
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val target = (scrubTarget * durationMs).toLong()
                            pendingSeekMs = target
                            // EXACT supaya mendarat presisi di detik yg dipilih
                            // (bukan snap ke keyframe jauh -> bug 2).
                            exoPlayer.setSeekParameters(SeekParameters.EXACT)
                            exoPlayer.seekTo(target)
                        }
                        isScrubbing = false
                        // Jari lepas -> lanjut play sesuai status sebelum scrub.
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

                // Mute / unmute (paling kanan)
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
                // --- Action bar (di BAWAH kontrol, dalam island yg sama) ---
                if (actionsSlot != null) {
                    Spacer(Modifier.height(4.dp))
                    actionsSlot()
                }
            }
        }
    }
}

/** Format milidetik -> "m:ss" (atau "h:mm:ss" kalau >= 1 jam). */
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
