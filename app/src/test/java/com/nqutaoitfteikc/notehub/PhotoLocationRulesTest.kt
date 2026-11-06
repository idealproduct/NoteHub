package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.achievements.MilestonePolicy
import com.nqutaoitfteikc.notehub.maps.PhotoMapLayers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.FeatureCollection

class PhotoLocationRulesTest {
    private val location=PhotoLocation(25.033,121.5654,5f,123456,true)
    @Test fun onlyFreshAccurateLocationsWithFinePermissionAreAccepted() {
        val at=20_000_000_000L
        assertEquals(location,PhotoLocationRules.snapshot(location,at,at,true,true).location)
        assertEquals(PhotoLocationStatus.STALE,PhotoLocationRules.snapshot(location,at,at+15_000_000_001L,true,true).status)
        assertEquals(PhotoLocationStatus.STALE,PhotoLocationRules.snapshot(location,at,at-1,true,true).status)
        assertEquals(PhotoLocationStatus.APPROXIMATE_ONLY,PhotoLocationRules.snapshot(location,at,at,false,true).status)
        assertEquals(PhotoLocationStatus.PERMISSION_DENIED,PhotoLocationRules.snapshot(location,at,at,false,false).status)
        listOf(location.copy(accuracyMeters=51f),location.copy(accuracyMeters=Float.NaN),location.copy(latitude=91.0),location.copy(longitude=Double.NaN)).forEach {
            assertNull(PhotoLocationRules.snapshot(it,at,at,true,true).location)
        }
    }
    @Test fun mapExcludesDraftsOldPhotosAndDeletedNotesAndDoesNotExposeTextInGeoJson() = runBlocking {
        val base=Photo("p","note","session","p.jpg",123,0,"UTC",CaptureTimeSource.CAPTURE_STARTED, "私密照片說明",location,PhotoLocationStatus.RECORDED)
        val notes=listOf(Note("note","私密記事標題",123))
        val data=PhotoMapRules.build(listOf(base,base.copy(id="old",location=null,locationStatus=PhotoLocationStatus.NOT_RECORDED),base.copy(id="draft",noteId=null),base.copy(id="deleted",noteId="gone")),notes)
        assertEquals(1,data.points.size); assertEquals(1,data.withoutLocation)
        val json=PhotoMapLayers.geoJson(data.points)
        assertFalse(json.contains("私密"))
        val feature=FeatureCollection.fromJson(json).features()!!.single()
        assertEquals("p",feature.getStringProperty("photo_id"))
        assertEquals(location,base.location)
    }
    @Test fun cumulativeProgressHasCorrectNextMilestoneAndNoOverflow() {
        val policy=MilestonePolicy(listOf(1,3,5))
        mapOf(0L to 1L,1L to 5L,5L to 10L,10L to 30L,30L to 50L,50L to 100L,100L to 300L).forEach { (total,next) -> assertEquals(next,policy.nextAfter(total)) }
        assertNull(policy.nextAfter(Long.MAX_VALUE))
    }
}
