package com.nqutaoitfteikc.notehub.photos

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PhotoFiles(private val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) { "無法建立照片儲存目錄" } }
    fun file(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.jpg"))) { "照片檔名格式不正確" }
        val result = File(directory, name)
        require(result.canonicalFile.parentFile == directory.canonicalFile) { "照片路徑不正確" }
        return result
    }
    fun write(name: String, bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= 25 * 1024 * 1024) { "照片大小不正確或超過 25 MB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth > 0 && bounds.outHeight > 0) { "相機未提供可讀取的 JPEG 照片" }
        val target = file(name)
        val partial = File(directory, "$name.part")
        try {
            FileOutputStream(partial).use { output -> output.write(bytes); output.fd.sync() }
            if (!partial.renameTo(target)) throw IOException("無法完成照片寫入")
        } finally { if (partial.exists() && !partial.delete()) throw IOException("無法清理未完成的照片檔案") }
    }
    fun remove(name: String) {
        val target = file(name)
        if (target.exists() && !target.delete()) throw IOException("無法移除照片檔案")
    }
    fun prune(referenced: Set<String>) {
        val files = directory.listFiles() ?: throw IOException("無法讀取照片目錄")
        files.filter { it.isFile && ((it.name.endsWith(".jpg") && it.name !in referenced) || it.name.endsWith(".jpg.part")) }.forEach {
            if (!it.delete()) throw IOException("無法清理未使用的照片檔案")
        }
    }
}
