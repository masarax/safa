package com.safa.account.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Converts a user-selected image into a bounded upload payload that satisfies
 * the backend logo validation contract without reading an unbounded original
 * directly into the multipart request.
 */
object LogoUploadPreparer {
    private const val MAX_DIMENSION = 1280
    private const val MAX_UPLOAD_BYTES = 1_800_000
    private const val MIN_QUALITY = 55

    data class PreparedLogo(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String,
    )

    fun prepare(context: Context, uri: Uri): PreparedLogo {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("The selected logo cannot be opened.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a valid image." }

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_DIMENSION * 2) {
            sampleSize *= 2
        }

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: throw IllegalArgumentException("The selected logo cannot be decoded.")

        val longest = max(decoded.width, decoded.height)
        val scaled = if (longest > MAX_DIMENSION) {
            val factor = MAX_DIMENSION.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * factor).toInt().coerceAtLeast(1),
                (decoded.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }

        try {
            var quality = 90
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { output ->
                    @Suppress("DEPRECATION")
                    check(scaled.compress(Bitmap.CompressFormat.WEBP, quality, output)) {
                        "The selected logo could not be encoded."
                    }
                    output.toByteArray()
                }
                quality -= 10
            } while (bytes.size > MAX_UPLOAD_BYTES && quality >= MIN_QUALITY)

            require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) {
                "The selected logo is too large. Choose a smaller image."
            }
            return PreparedLogo(bytes = bytes, mimeType = "image/webp", fileName = "safa-logo.webp")
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}
