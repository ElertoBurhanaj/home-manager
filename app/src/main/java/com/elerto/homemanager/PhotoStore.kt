package com.elerto.homemanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/** Copia le foto nella memoria privata dell'app (ridimensionate) e ne gestisce le anteprime. */
object PhotoStore {

    // Cache limitata in byte (circa 48 MB), cosi' le foto grandi non saturano la memoria.
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    private fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    /** Importa una foto da un Uri e restituisce il nome del file salvato, o null se fallisce. */
    fun save(ctx: Context, uri: Uri): String? {
        return try {
            val resolver = ctx.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bitmap == null) {
                    null
                } else {
                    val orientation = resolver.openInputStream(uri)?.use {
                        ExifInterface(it).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL

                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    }
                    val result: Bitmap = if (matrix.isIdentity) {
                        bitmap
                    } else {
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    val name = UUID.randomUUID().toString() + ".jpg"
                    FileOutputStream(file(ctx, name)).use {
                        result.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    }
                    name
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Anteprima ridimensionata (lato lungo circa maxPx), con cache in memoria. */
    fun thumb(ctx: Context, name: String, maxPx: Int): ImageBitmap? {
        val key = "$name@$maxPx"
        cache.get(key)?.let { return it }

        val f = file(ctx, name)
        if (!f.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2

        val bitmap = BitmapFactory.decodeFile(
            f.path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }

    fun delete(ctx: Context, name: String) {
        file(ctx, name).delete()
        cache.evictAll()
    }
}
