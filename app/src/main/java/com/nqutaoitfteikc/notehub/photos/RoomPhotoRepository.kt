package com.nqutaoitfteikc.notehub.photos

import androidx.room.withTransaction
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.time.ZoneId

class RoomPhotoRepository(private val database: NoteDatabase, val files: PhotoFiles, private val vault: com.nqutaoitfteikc.notehub.privacy.LocalVault? = null, private val scheduleCleanup: () -> Unit) : PhotoRepository {
    private val dao = database.photos()
    private val mutex = Mutex()
    private fun encode(point: PhotoLocation)=java.io.ByteArrayOutputStream().use { out ->
        java.io.DataOutputStream(out).use { it.writeDouble(point.latitude); it.writeDouble(point.longitude); it.writeFloat(point.accuracyMeters); it.writeLong(point.measuredAt); it.writeBoolean(point.isMock) }; out.toByteArray()
    }
    private fun sealed(row: PhotoEntity, point: PhotoLocation)=row.copy(latitude=null,longitude=null,locationAccuracy=null,locationMeasuredAt=null,locationMock=false,
        sealedLocation=android.util.Base64.encodeToString(requireNotNull(vault).seal(encode(point),"location:${row.id}"),android.util.Base64.NO_WRAP))
    private fun domain(row: PhotoEntity): Photo {
        val blob=row.sealedLocation ?: return row.toDomain()
        val bytes=requireNotNull(vault) { "無法取得照片定位加密金鑰" }.open(android.util.Base64.decode(blob,android.util.Base64.NO_WRAP),"location:${row.id}")
        val point=java.io.DataInputStream(bytes.inputStream()).use { PhotoLocation(it.readDouble(),it.readDouble(),it.readFloat(),it.readLong(),it.readBoolean()).validated() }
        return row.toDomain().copy(location=point,locationStatus=PhotoLocationStatus.RECORDED)
    }
    override fun observe() = dao.observe().map { rows -> withContext(Dispatchers.IO) { rows.mapIndexed { index,row -> if(index%256==0) ensureActive(); domain(row) } } }
    override fun pendingCleanupCount() = dao.pendingCleanupCount()
    override suspend fun store(sessionId: String, capture: CapturedImage): Photo = mutex.withLock {
        UUID.fromString(sessionId)
        require(capture.time.millis > 0) { "相機拍攝時間不正確" }
        require(capture.rotationDegrees in listOf(0, 90, 180, 270)) { "照片方向不正確" }
        ZoneId.of(capture.zoneId)
        capture.location.validated()
        require(dao.session(sessionId).all { it.noteId == null }) { "此拍攝草稿已儲存，請建立新的拍攝草稿" }
        val id = UUID.randomUUID().toString()
        var row = PhotoEntity(id, null, sessionId, "$id.jpg", capture.time.millis, capture.rotationDegrees,
            capture.zoneId, capture.time.source.name, "", capture.location.location?.latitude,
            capture.location.location?.longitude,capture.location.location?.accuracyMeters,capture.location.location?.measuredAt,
            capture.location.location?.isMock ?: false,capture.location.status.name)
        if(vault!=null && capture.location.location!=null) row=withContext(Dispatchers.IO) { sealed(row,requireNotNull(capture.location.location)) }
        withContext(Dispatchers.IO) { files.write(row.fileName, capture.bytes) }
        try { dao.insert(row) }
        catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { files.remove(row.fileName) }
            throw e
        }
        withContext(Dispatchers.IO) { domain(row) }
    }
    override suspend fun commit(sessionId: String, targetNoteId: String?, title: String, description: String, category: String): String = mutex.withLock {
        val valid = NoteDraft(title, description, category).validated()
        database.withTransaction {
            val rows = dao.session(sessionId)
            val drafts = rows.filter { it.noteId == null }
            if (drafts.isEmpty()) {
                val committed = rows.mapNotNull { it.noteId }.distinct().singleOrNull()
                require(committed != null && (targetNoteId == null || committed == targetNoteId)) { "請至少拍攝一張照片" }
                return@withTransaction committed
            }
            require(rows.size == drafts.size) { "拍攝草稿狀態不一致" }
            val id = if (targetNoteId == null) {
                val note = PhotoNoteRules.create(UUID.randomUUID().toString(), valid.title, valid.description, valid.category, drafts.map { domain(it) })
                database.notes().insert(note.toEntity())
                com.nqutaoitfteikc.notehub.achievements.AchievementRecorder(database.achievements(),com.nqutaoitfteikc.notehub.achievements.MilestonePolicy(listOf(1,3,5))::contains).recordNewRecord(System.currentTimeMillis())
                note.id
            } else {
                val existing = requireNotNull(database.notes().get(targetNoteId)) { "原記事已不存在，照片仍保留在草稿中" }
                require(existing.photoCapturedAt != null) { "請選擇照片記事" }
                database.notes().update(existing.copy(title = valid.title, description = valid.description, category = valid.category))
                targetNoteId
            }
            dao.attach(sessionId, id)
            id
        }
    }
    override suspend fun remove(id: String) = mutex.withLock {
        database.withTransaction {
            dao.get(id)?.let { dao.enqueueDeletion(PhotoDeletionEntity(it.fileName)); dao.delete(id) }
        }
        scheduleCleanup()
    }
    override suspend fun discard(sessionId: String) = mutex.withLock {
        database.withTransaction {
            dao.session(sessionId).filter { it.noteId == null }.forEach {
                dao.enqueueDeletion(PhotoDeletionEntity(it.fileName)); dao.delete(it.id)
            }
        }
        scheduleCleanup()
    }
    override suspend fun caption(id: String, value: String) {
        require(value.length <= 2000) { "照片說明最多 2000 個字元" }
        check(dao.caption(id, value) == 1) { "照片已不存在" }
    }
    suspend fun deleteNoteInTransaction(noteId: String, action: suspend () -> Unit) = mutex.withLock {
        database.withTransaction {
            dao.forNote(noteId).forEach { dao.enqueueDeletion(PhotoDeletionEntity(it.fileName)) }
            action()
        }
        scheduleCleanup()
    }
    override suspend fun recoverFiles() = mutex.withLock {
        withContext(Dispatchers.IO) {
            for (entry in dao.deletions()) {
                files.remove(entry.fileName)
                dao.finishDeletion(entry.fileName)
            }
            val rows=dao.all()
            if(vault!=null) rows.forEach { row ->
                files.protect(row.fileName)
                if(row.sealedLocation==null) row.toDomain().location?.let { point -> dao.sealLocation(row.id,requireNotNull(sealed(row,point).sealedLocation)) }
            }
            files.prune(rows.map { it.fileName }.toSet())
        }
    }
}
