package id.andreasmbngaol.agallery.presentation.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import id.andreasmbngaol.agallery.domain.model.GallerySortOrder
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.presentation.animation.sharedPhotoElement
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import org.koin.androidx.compose.koinViewModel

/**
 * Viewer full-screen: swipe antar foto (HorizontalPager), pinch-to-zoom
 * (Telephoto), swipe-down untuk menutup, dan shared element transition
 * dari/menuju thumbnail di grid.
 *
 * - [initialIndex] menentukan halaman awal pager — tap foto ke-N langsung
 *   buka di halaman ke-N tanpa perlu mencari mediaId di stream paging.
 * - [mediaId] disimpan sebagai stable identity untuk kebutuhan berikutnya
 *   (share/delete/verify), meskipun belum dipakai sekarang.
 *
 * Shared element:
 * - Modifier `sharedPhotoElement` di-pasang pada Box WRAPPER di sekitar
 *   [ZoomableAsyncImage], bukan pada [ZoomableAsyncImage] itu sendiri. Ini
 *   mengisolasi bounds animation dari transformasi internal Telephoto
 *   (yang punya subsampling/matrix sendiri) supaya tidak muncul artifact
 *   "membesar dulu baru bergeser" saat exit.
 * - Hanya halaman AKTIF (yang sedang tampak di pager) yang menerima
 *   sharedPhotoElement. Halaman lain (preload kiri/kanan) tidak, supaya
 *   thumbnail di grid tidak salah cocok saat transisi.
 *
 * Swipe-down:
 * - Aktif hanya saat halaman tampak DAN foto belum di-zoom (zoomFraction ≈ 0).
 * - Threshold 120dp → [onBack] dipanggil (mengalir ke pop transition +
 *   shared element mengecil kembali ke thumbnail).
 */
@Composable
fun PhotoViewerScreen(
    mediaId: Long,
    initialIndex: Int,
    sortOrder: GallerySortOrder,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = koinViewModel(),
) {
    BackHandler(onBack = onBack)

    // Samakan urutan paging viewer dengan grid; kalau tidak, initialIndex
    // (posisi tap di grid) menunjuk foto yang salah saat sort dibalik.
    LaunchedEffect(sortOrder) {
        viewModel.setSortOrder(sortOrder)
    }

    val items = viewModel.media.collectAsLazyPagingItems()

    // Drag state di-hoist ke level screen supaya latar viewer (ikut theme:
    // putih di light mode, gelap di dark) juga memudar seiring drag — hint
    // visual bahwa gesture akan menutup.
    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val dismissFraction by remember(dismissThresholdPx) {
        derivedStateOf {
            (dragOffsetY.value / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
                    .copy(alpha = 1f - dismissFraction * 0.6f),
            ),
    ) {
        val count = items.itemCount
        if (count == 0) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, count - 1),
            pageCount = { items.itemCount },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // key() memastikan tiap media punya ZoomableImageState sendiri,
                // sehingga zoom di halaman lain tidak "bocor" saat pager reuse slot.
                key(item.id) {
                    PhotoViewerPage(
                        item = item,
                        isActive = page == pagerState.currentPage,
                        dragOffsetY = dragOffsetY,
                        dismissThresholdPx = dismissThresholdPx,
                        onDismiss = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoViewerPage(
    item: MediaItem,
    isActive: Boolean,
    dragOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onDismiss: () -> Unit,
) {
    val imageState = rememberZoomableImageState()
    // Swipe-down hanya aktif pada halaman yang sedang tampak DAN saat foto
    // belum di-zoom, supaya gestur pan di zoomable tidak bentrok.
    val canDismiss by remember(imageState, isActive) {
        derivedStateOf {
            isActive && (imageState.zoomableState.zoomFraction ?: 0f) < 0.01f
        }
    }
    val scope = rememberCoroutineScope()

    // Shared element modifier di-attach HANYA ke halaman aktif, dan HANYA ke
    // wrapper Box (bukan langsung ke ZoomableAsyncImage) — lihat KDoc kelas.
    val sharedModifier = if (isActive) {
        Modifier.sharedPhotoElement(key = "photo-${item.id}")
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffsetY.value
                alpha = 1f - (dragOffsetY.value / dismissThresholdPx)
                    .coerceIn(0f, 1f) * 0.4f
            }
            .pointerInput(canDismiss) {
                if (!canDismiss) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        // Hanya proses drag ke bawah, atau drag balik ke 0.
                        if (delta > 0f || dragOffsetY.value > 0f) {
                            val next = (dragOffsetY.value + delta).coerceAtLeast(0f)
                            change.consume()
                            scope.launch { dragOffsetY.snapTo(next) }
                        }
                    },
                    onDragEnd = {
                        if (dragOffsetY.value >= dismissThresholdPx) {
                            onDismiss()
                        } else {
                            scope.launch { dragOffsetY.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetY.animateTo(0f) }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier),
        ) {
            ZoomableAsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = item.uri,
                contentDescription = item.displayName,
                state = imageState,
            )
        }
    }
}
