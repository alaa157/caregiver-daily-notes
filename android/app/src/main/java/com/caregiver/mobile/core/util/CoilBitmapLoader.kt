package com.caregiver.mobile.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.runBlocking

@UnstableApi
class CoilBitmapLoader(private val context: Context) : androidx.media3.common.util.BitmapLoader {
    private val imageLoader = ImageLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun loadBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<Bitmap> {
        return loadBitmap(data, null)
    }

    override fun loadBitmap(data: ByteArray, callback: androidx.media3.common.util.BitmapLoader.Callback?): com.google.common.util.concurrent.ListenableFuture<Bitmap> {
        val future = com.google.common.util.concurrent.SettableFuture.create<Bitmap>()
        try {
            val bitmap = runBlocking {
                val request = ImageRequest.Builder(context).data(data).build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()?.let { src ->
                    // Independent copy so media3 never receives a recycled shared bitmap.
                    val copy = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(copy)
                    canvas.drawBitmap(src, 0f, 0f, null)
                    copy
                } ?: Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            }
            future.set(bitmap)
        } catch (e: Exception) {
            future.set(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        }
        return future
    }
}
