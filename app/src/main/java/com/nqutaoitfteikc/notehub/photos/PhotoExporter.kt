package com.nqutaoitfteikc.notehub.photos

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.nqutaoitfteikc.notehub.domain.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Export a copy; private originals and capture records are never modified. */
class PhotoExporter(private val context: Context, private val files: PhotoFiles) {
    suspend fun export(photo: Photo, destination: Uri) = withContext(Dispatchers.IO) {
        val source = files.file(photo.fileName)
        if (!source.isFile) throw IOException("找不到照片檔案")
        val temporary = File.createTempFile("photo-export-", ".jpg", context.cacheDir)
        try {
            source.copyTo(temporary, overwrite = true)
            ExifInterface(temporary.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, when (photo.rotationDegrees) {
                    90 -> ExifInterface.ORIENTATION_ROTATE_90
                    180 -> ExifInterface.ORIENTATION_ROTATE_180
                    270 -> ExifInterface.ORIENTATION_ROTATE_270
                    else -> ExifInterface.ORIENTATION_NORMAL
                }.toString())
                val captured = Instant.ofEpochMilli(photo.capturedAt).atZone(ZoneId.of(photo.zoneId))
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
                    captured.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)))
                setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, "%03d".format(Locale.ROOT, captured.nano / 1_000_000))
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, captured.offset.id.let { if (it == "Z") "+00:00" else it })
                saveAttributes()
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("無法開啟匯出位置")
            output.use { target -> temporary.inputStream().use { it.copyTo(target) } }
        } finally { temporary.delete() }
    }
}
