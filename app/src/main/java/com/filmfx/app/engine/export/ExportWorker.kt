package com.filmfx.app.engine.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.*
import com.filmfx.app.data.EffectParameters
import com.filmfx.app.engine.EngineContext
import jp.co.cyberagent.android.gpuimage.GPUImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStr = inputData.getString(KEY_INPUT_URI) ?: return@withContext Result.failure()
        val paramsJson = inputData.getString(KEY_PARAMS) ?: return@withContext Result.failure()
        
        val inputUri = Uri.parse(uriStr)
        val params = try {
            Json.decodeFromString<EffectParameters>(paramsJson)
        } catch (e: Exception) {
            return@withContext Result.failure()
        }

        setProgress(workDataOf(KEY_PROGRESS to 5))
        
        // Read original dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(inputUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var origWidth = options.outWidth
        var origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) origWidth = 1000.also { origHeight = 1000 }

        // Handle Orientation swap for bounds
        var originalExif: ExifInterface? = null
        applicationContext.contentResolver.openInputStream(inputUri)?.use {
            originalExif = ExifInterface(it)
            val orientation = originalExif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                val temp = origWidth
                origWidth = origHeight
                origHeight = temp
            }
        }

        var attempt = 0
        var resultBitmap: Bitmap? = null
        var finalWidth = origWidth
        var finalHeight = origHeight
        
        while (attempt < 15) {
            val dimensions = ResolutionFallback.getDimensionsForAttempt(origWidth, origHeight, attempt)
                ?: break
            
            val targetWidth = dimensions.first
            val targetHeight = dimensions.second
            
            try {
                setProgress(workDataOf(KEY_PROGRESS to 10 + attempt))
                Log.d("ExportWorker", "Attempt ${attempt+1}: Rendering at ${targetWidth}x${targetHeight}")
                
                // Load bitmap scaled
                val scale = Math.min(origWidth.toFloat() / targetWidth, origHeight.toFloat() / targetHeight)
                val inOptions = BitmapFactory.Options().apply {
                    inSampleSize = if (scale > 1) scale.toInt() else 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                
                val sourceBmp = applicationContext.contentResolver.openInputStream(inputUri)?.use {
                    BitmapFactory.decodeStream(it, null, inOptions)
                } ?: throw RuntimeException("Failed to decode input")
                
                // Apply filters
                val filterGroup = EngineContext.buildFilterGroup(params, targetWidth.toFloat(), targetHeight.toFloat())
                val gpuImage = GPUImage(applicationContext)
                gpuImage.setFilter(filterGroup)
                
                val processedBmp = gpuImage.getBitmapWithFilterApplied(sourceBmp)
                sourceBmp.recycle()
                
                if (processedBmp == null) {
                    throw RuntimeException("GPUImage returned null")
                }
                
                // If we get here, no OOM!
                resultBitmap = processedBmp
                finalWidth = targetWidth
                finalHeight = targetHeight
                break
            } catch (e: OutOfMemoryError) {
                Log.w("ExportWorker", "OOM on attempt $attempt. Falling back.", e)
                attempt++
            } catch (e: Exception) {
                Log.e("ExportWorker", "Error on attempt $attempt: ${e.message}", e)
                if (e.message?.contains("glCheckFramebufferStatus") == true || e.message?.contains("allocate") == true) {
                    attempt++ // Treat GL frame buffer issues as OOM
                } else {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to e.message))
                }
            }
        }
        
        if (resultBitmap == null) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "All fallback attempts failed (OOM)."))
        }
        
        setProgress(workDataOf(KEY_PROGRESS to 80))
        
        // Save to DCIM/FilmFX
        val outFileName = getOutputFileName(inputUri)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, outFileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/FilmFX")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        
        val contentResolver = applicationContext.contentResolver
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Failed to create MediaStore entry"))
        
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                resultBitmap!!.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            resultBitmap!!.recycle()
            
            // Write EXIF
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val outExif = ExifInterface(pfd.fileDescriptor)
                // Copy basic attributes
                originalExif?.let { inExif ->
                    val tagsToCopy = listOf(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        ExifInterface.TAG_DATETIME_DIGITIZED,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_APERTURE_VALUE,
                        ExifInterface.TAG_ISO_SPEED_RATINGS
                    )
                    tagsToCopy.forEach { tag ->
                        inExif.getAttribute(tag)?.let { value ->
                            outExif.setAttribute(tag, value)
                        }
                    }
                }
                
                // Inject FilmFX specific 
                outExif.setAttribute(ExifInterface.TAG_SOFTWARE, "FilmFX")
                outExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, finalWidth.toString())
                outExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, finalHeight.toString())
                outExif.saveAttributes()
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            
            setProgress(workDataOf(KEY_PROGRESS to 100))
            return@withContext Result.success()
            
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            return@withContext Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    private fun getOutputFileName(sourceUri: Uri): String {
        var baseName = "photo"
        applicationContext.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val original = cursor.getString(nameIndex)
                    baseName = original.substringBeforeLast(".")
                }
            }
        }
        return "${baseName}_FFX.jpg"
    }

    companion object {
        const val KEY_INPUT_URI = "inputUri"
        const val KEY_PARAMS = "params"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}
