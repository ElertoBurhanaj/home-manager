package com.elerto.homemanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Copia le foto nella memoria privata dell'app (ridimensionate) e ne gestisce le anteprime. */
object PhotoStore {

    // Cache limitata in byte (circa 48 MB), cosi' le foto grandi non saturano la memoria.
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    private fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    /** File di una foto salvata (usato da backup e ripristino). */
    fun fileOf(ctx: Context, name: String): File = file(ctx, name)

    /** Crea una copia indipendente di una foto (per duplicare un oggetto). */
    fun copy(ctx: Context, name: String): String? {
        val src = file(ctx, name)
        if (!src.exists()) return null
        val newName = UUID.randomUUID().toString() + ".jpg"
        return try {
            src.copyTo(file(ctx, newName))
            newName
        } catch (e: Exception) {
            null
        }
    }

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


    /**
     * "Ordina foto": isola l'oggetto dallo sfondo (ML Kit, sul telefono), lo ritaglia, lo centra
     * su uno sfondo neutro quadrato e salva il risultato come nuova foto. L'originale non viene toccato.
     * Va chiamata fuori dal thread principale. Restituisce il nome del nuovo file o null se fallisce.
     */
    fun tidy(ctx: Context, name: String): String? {
        val f = file(ctx, name)
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null

            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            val fgOrNull: Bitmap? = try {
                Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0))).foregroundBitmap
            } finally {
                segmenter.close()
            }
            val fg: Bitmap = fgOrNull ?: return null

            // Riquadro dei pixel non trasparenti.
            val w = fg.width
            val h = fg.height
            val px = IntArray(w * h)
            fg.getPixels(px, 0, w, 0, 0, w, h)
            var left = w
            var right = -1
            var top = h
            var bottom = -1
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if ((px[y * w + x] ushr 24) > 40) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
            if (right < left || bottom < top) return null
            val cropW = right - left + 1
            val cropH = bottom - top + 1
            // Se l'oggetto trovato e' minuscolo, la segmentazione non ha funzionato.
            if (cropW.toLong() * cropH < w.toLong() * h / 40) return null

            val side = 1200
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(0xFFF2F2EF.toInt())

            val scale = min(side * 0.84f / cropW, side * 0.84f / cropH)
            val dw = cropW * scale
            val dh = cropH * scale
            val dst = RectF((side - dw) / 2f, (side - dh) / 2f, (side + dw) / 2f, (side + dh) / 2f)

            // Ombra morbida sotto l'oggetto.
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x30000000
                maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawOval(
                RectF(dst.left + dw * 0.08f, dst.bottom - dh * 0.02f, dst.right - dw * 0.08f, dst.bottom + dh * 0.04f + 6f),
                shadow,
            )
            canvas.drawBitmap(fg, Rect(left, top, right + 1, bottom + 1), dst, Paint(Paint.FILTER_BITMAP_FLAG))

            val newName = UUID.randomUUID().toString() + ".jpg"
            FileOutputStream(file(ctx, newName)).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            newName
        } catch (e: Exception) {
            null
        }
    }

    /** Ruota una foto di 90° in senso orario e salva il risultato come nuovo file (l'originale non cambia). */
    fun rotate(ctx: Context, name: String): String? {
        val f = file(ctx, name)
        if (!f.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(f.path) ?: return null
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val newName = UUID.randomUUID().toString() + ".jpg"
            FileOutputStream(file(ctx, newName)).use { rotated.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            newName
        } catch (e: Throwable) {
            // Include la mancanza di memoria con foto molto grandi.
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
