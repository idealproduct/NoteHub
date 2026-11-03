package com.nqutaoitfteikc.notehub.photos

import android.content.Context
import android.net.Uri
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.nqutaoitfteikc.notehub.domain.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Export a copy; private originals and capture records are never modified. */
class PhotoExporter(private val context: Context, private val files: PhotoFiles) {
    suspend fun toAlbum(photo: Photo): Uri = withContext(Dispatchers.IO) {
        val name = "NoteHub_${java.util.UUID.randomUUID()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NoteHub")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NoteHub")
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("無法建立 NoteHub 相簿")
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(folder, name).absolutePath)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("無法建立相簿照片")
        try {
            export(photo, uri)
            if (Build.VERSION.SDK_INT >= 29) {
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) { "無法完成相簿照片儲存" }
            }
            uri
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                try { resolver.delete(uri, null, null) }
                catch (cleanup: Exception) { Log.e("NoteHubPhotos", "未完成的相簿副本清理失敗", cleanup) }
            }
            throw failure
        }
    }
    suspend fun export(photo: Photo, destination: Uri) = withContext(Dispatchers.IO) {
        val source = files.file(photo.fileName)
        if (!source.isFile) throw IOException("找不到照片檔案")
        val temporary = File.createTempFile("photo-export-", ".jpg", context.cacheDir)
        try {
            temporary.writeBytes(com.nqutaoitfteikc.notehub.privacy.JpegPrivacy.strip(files.read(photo.fileName)))
            ExifInterface(temporary.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, when (photo.rotationDegrees) {
                    90 -> ExifInterface.ORIENTATION_ROTATE_90
                    180 -> ExifInterface.ORIENTATION_ROTATE_180
                    270 -> ExifInterface.ORIENTATION_ROTATE_270
                    else -> ExifInterface.ORIENTATION_NORMAL
                }.toString())
                saveAttributes()
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("無法開啟匯出位置")
            output.use { target -> temporary.inputStream().use { it.copyTo(target) } }
        } finally { temporary.delete() }
    }
}
