package com.nqutaoitfteikc.notehub.achievements

import android.content.*
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.nqutaoitfteikc.notehub.data.AchievementDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Explicit private PNG sharing only. Never writes to shared storage. */
class AchievementExporter(private val context: Context, private val dao: AchievementDao) {
    private val mutex=Mutex()
    suspend fun shareIntent(milestone: Long, svg: Boolean): Intent = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(!svg) { "僅提供 PNG 分享" }
            require(dao.get(milestone)!=null) { "成就尚未達成" }
            val folder=File(context.filesDir,"achievement-share").apply { check(isDirectory || mkdirs()) }
            val file=File(folder,"NoteHub-$milestone.png")
            val bitmap=AchievementCover.bitmap(milestone)
            try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
            finally { bitmap.recycle() }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.achievements",file)
            Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="image/png"
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newRawUri("NoteHub 成就",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"分享成就至…")
        }
    }
}
