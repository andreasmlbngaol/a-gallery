package id.andreasmbngaol.agallery.presentation.viewer

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Pause
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.SpeakerSlash
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import id.andreasmbngaol.agallery.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.media3.common.MediaItem as ExoMediaItem

// ---- Tuning liquid glass (samain dengan floating nav bar) ----
private val GlassBlurRadius = 4.dp
private val GlassRefractionHeight = 12.dp
private val GlassRefractionAmount = 16.dp
private const val GlassTintAlpha = 0.3f
// Fallback API < 33 (tanpa RuntimeShader): container solid semi-transparan.
private const val FrostedFallbackAlpha = 0.7f

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
 * - **Tap video** untuk hide/show baris kontrol.
 *
 * ## Fix bug slider "loncat balik"
 * Saat user selesai menyeret (mis. dari 0:01 ke 0:10), ExoPlayer.seekTo itu
 * async. Kalau kita langsung baca currentPosition, sempat frame ia masih
 * 0:01 -> thumb loncat balik dulu. Solusinya [pendingSeekMs]: setelah seek,
 * posisi yang DITAMPILKAN dikunci ke target sampai player benar-benar nyusul
 * (selisih <= 350ms), baru lanjut polling normal.
 *
 * ## Liquid glass di atas video
 * PlayerView dipaksa `surface_type=texture_view` (lihat res/layout/
 * view_video_player.xml) supaya frame video ikut ter-render di view hierarchy
 * dan bisa di-capture sebagai [layerBackdrop] untuk dibiaskan panel kaca.
 */
@Composable
fun VideoPlayerContent(
    uri: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val liquidGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val backdrop = rememberLayerBackdrop()

    // Satu ExoPlayer per video. remember(uri) -> instance baru kalau uri ganti.
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // default mute
            prepare()
        }
    }

    // Mute pakai state SESSION (VideoPlaybackPrefs) supaya konsisten antar video
    // selama app dibuka; lihat KDoc object-nya.
    val isMuted = VideoPlaybackPrefs.isMuted
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    // true selama user menyeret slider -> jangan override dari player.
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableFloatStateOf(0f) }
    // Target seek yang "dikunci" sampai player nyusul (anti loncat-balik).
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }

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
            delay(200)
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
            if (liquidGlassSupported) {
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

        // --- Tap area: toggle visibility kontrol ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                },
        )

        // --- Baris kontrol (glass) di bawah ---
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val glassTint =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = GlassTintAlpha)
            val fallbackTint =
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedFallbackAlpha)
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
                if (liquidGlassSupported) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(GlassBlurRadius.toPx())
                            lens(GlassRefractionHeight.toPx(), GlassRefractionAmount.toPx())
                        },
                        onDrawSurface = { drawRect(glassTint) },
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(28.dp)).background(fallbackTint)
                }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .then(containerModifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            PhosphorIcons.Regular.Pause
                        } else {
                            PhosphorIcons.Regular.Play
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
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
                        isScrubbing = true
                        scrubTarget = value
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val target = (scrubTarget * durationMs).toLong()
                            pendingSeekMs = target
                            exoPlayer.seekTo(target)
                        }
                        isScrubbing = false
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
                            PhosphorIcons.Regular.SpeakerSlash
                        } else {
                            PhosphorIcons.Regular.SpeakerHigh
                        },
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = contentColor,
                    )
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
