package io.bashpsk.thumbnails

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ThumbnailView(
    modifier: Modifier = Modifier,
    artworkUri: () -> String,
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
    compression: () -> Int = { 100 }
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val emptyThumbnail = remember(key1 = artworkUri()) {
        EmptyThumbnail(context).apply {

            setArtwork(uri = artworkUri())
            setCompression(level = compression())
        }
    }

    val thumbnailState by emptyThumbnail.thumbnailState.collectAsStateWithLifecycle(
        initialValue = null
    )

    DisposableEffect(key1 = thumbnailState) {

        coroutineScope.launch(context = Dispatchers.IO) {

            when (thumbnailState == ThumbnailState.INIT) {

                true -> emptyThumbnail.setThumbnailLoader()
                false -> {}
            }
        }

        onDispose {

//            emptyThumbnail.removeThumbnailLoader()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {

        when (thumbnailState ?: ThumbnailState.INIT) {

            is ThumbnailState.INIT -> loading()
            is ThumbnailState.LOADING -> loading()
            is ThumbnailState.ERROR -> error()
            is ThumbnailState.SUCCESS -> Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = (thumbnailState as ThumbnailState.SUCCESS).bitmap.asImageBitmap(),
                contentScale = ContentScale.Crop,
                contentDescription = "Media Thumbnail"
            )
        }
    }
}