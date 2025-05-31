package io.bashpsk.thumbnails

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class EmptyThumbnail(private val context: Context) {

    private val LOG_TAG = "EMPTY-THUMBNAIL"

    private val lifecycleScope = CoroutineScope(context = SupervisorJob() + Dispatchers.IO)

    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->

        Log.e(LOG_TAG, throwable.message, throwable)
    }

    private val _artwork = MutableStateFlow(value = "")
    val artwork = _artwork.asStateFlow()

    private val _compression = MutableStateFlow(value = 100)
    val compression = _compression.asStateFlow()

    private val _thumbnailState = MutableStateFlow<ThumbnailState>(value = ThumbnailState.INIT)
    val thumbnailState = _thumbnailState.asStateFlow()

    fun setArtwork(uri: String) {

        _artwork.update { uri }
    }

    fun setCompression(level: Int) {

        _compression.update { level }
    }

    private fun getThumbnailName(): String {

        return "${artwork.value.hashCode()}-${compression.value}.JPG"
    }

    private fun retrieveThumbnail(): Flow<ThumbnailState> {

        return flow {

            emit(value = ThumbnailState.LOADING)

            try {

                when {

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {

                        context.contentResolver.loadThumbnail(
                            artwork.value.toUri(),
                            Size(-1, -1),
                            null
                        ).also { bitmapResult ->

                            saveThumbnail(bitmap = bitmapResult)
                        }
                    }

                    else -> {

                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            getVideoId(videoUri = artwork.value.toUri()),
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        ).let { bitmap ->

                            when (bitmap != null) {

                                true -> saveThumbnail(bitmap = bitmap)
                                false -> {}
                            }
                        }
                    }
                }

                emit(value = getThumbnail().lastOrNull() ?: ThumbnailState.ERROR())

            } catch (exception: Exception) {

                Log.e(LOG_TAG, exception.message, exception)

                when (exception) {

                    is CancellationException -> throw exception
                }

                emit(value = ThumbnailState.ERROR(message = "Artwork Null."))
            }
        }
    }

    private fun getVideoId(videoUri: Uri): Long {

        val videoId = MutableStateFlow(value = 0L)

        val projection = arrayOf(MediaStore.Video.Media._ID)

        context.contentResolver.query(
            videoUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->

            val vIdC = cursor.getColumnIndex(MediaStore.Video.Media._ID)

            when {

                cursor.moveToFirst() -> videoId.update { cursor.getLong(vIdC) }
            }
        }

        return videoId.value
    }

    private fun saveThumbnail(bitmap: Bitmap) {

        val thumbnailFile = File(context.cacheDir, getThumbnailName())

        FileOutputStream(thumbnailFile).use { outputStream ->

            bitmap.compress(Bitmap.CompressFormat.JPEG, compression.value, outputStream)
        }
    }

    private fun getThumbnail(): Flow<ThumbnailState> {

        return flow {

            val thumbnailFile = File(context.cacheDir, getThumbnailName())
            val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

            emit(value = ThumbnailState.LOADING)

            when (bitmap == null) {

                true -> emit(value = ThumbnailState.ERROR())
                false -> emit(value = ThumbnailState.SUCCESS(bitmap = bitmap))
            }
        }
    }

    fun getThumbnailState(): Flow<ThumbnailState> {

        return when (File(context.cacheDir, getThumbnailName()).exists()) {

            true -> getThumbnail()
            false -> retrieveThumbnail()
        }
    }

    suspend fun setThumbnailLoader() {

        getThumbnailState().collectLatest { thumbnailStateLatest ->

            _thumbnailState.update { thumbnailStateLatest }
        }
    }

    fun removeThumbnailLoader() {

        _thumbnailState.update { ThumbnailState.INIT }
    }
}