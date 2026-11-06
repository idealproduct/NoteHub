package com.nqutaoitfteikc.notehub.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class PhotoMapPoint(val photoId:String,val noteId:String,val title:String,val caption:String,val capturedAt:Long,val location:PhotoLocation)
data class PhotoMapData(val points:List<PhotoMapPoint> = emptyList(),val withoutLocation:Int=0)
object PhotoMapRules {
    suspend fun build(photos:List<Photo>,notes:List<Note>):PhotoMapData {
        val available=notes.associateBy { it.id }
        val points=ArrayList<PhotoMapPoint>()
        var missing=0
        photos.forEachIndexed { index,photo ->
            if(index%256==0) coroutineContext.ensureActive()
            val note=photo.noteId?.let { available[it] } ?: return@forEachIndexed
            val location=photo.location
            val valid=location!=null && photo.locationStatus==PhotoLocationStatus.RECORDED && runCatching { location.validated() }.isSuccess
            if(valid) points.add(PhotoMapPoint(photo.id,note.id,note.title,photo.caption,photo.capturedAt,requireNotNull(location))) else missing++
        }
        return PhotoMapData(points,missing)
    }
}
class ObservePhotoMap(private val photos:PhotoRepository,private val notes:NoteRepository) {
    operator fun invoke()=combine(photos.observe(),notes.observe()) { p,n -> withContext(Dispatchers.Default) { PhotoMapRules.build(p,n) } }
}
