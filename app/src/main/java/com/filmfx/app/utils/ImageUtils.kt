package com.filmfx.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream
import kotlin.math.max

object ImageUtils {

    /**
     * Loads an image from a URI and downsamples it so its maximum dimension is at most maxDimension.
     */
    fun loadAndDownsampleImage(context: Context, uri: Uri, maxDimension: Int = 1080): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            // First pass to get dimensions
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate inSampleSize
            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // Second pass to decode with inSampleSize
            inputStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            
            // Exact scaling if still slightly too large
            if (bitmap != null) {
                val maxScale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
                if (maxScale < 1.0f) {
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * maxScale).toInt(),
                        (bitmap.height * maxScale).toInt(),
                        true
                    )
                    if (scaled != bitmap) {
                        bitmap.recycle()
                    }
                    scaled
                } else bitmap
            } else null

        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }
}
