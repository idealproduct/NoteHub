package com.nqutaoitfteikc.notehub.domain

/** GPS evidence is captured with the shutter, never reconstructed from the save time. */
data class PhotoLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float,
                         val measuredAt: Long, val isMock: Boolean = false) {
    fun validated(): PhotoLocation {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "照片位置座標不正確" }
        require(accuracyMeters.isFinite() && accuracyMeters > 0 && accuracyMeters <= 50f && measuredAt > 0) { "照片定位精確度或時間不正確" }
        return this
    }
}
enum class PhotoLocationStatus { NOT_RECORDED, RECORDED, PERMISSION_DENIED, APPROXIMATE_ONLY, GPS_DISABLED, SEARCHING, STALE, INACCURATE, ERROR }
data class LocationSnapshot(val location: PhotoLocation? = null, val status: PhotoLocationStatus = PhotoLocationStatus.NOT_RECORDED) {
    fun validated(): LocationSnapshot {
        require((location != null) == (status == PhotoLocationStatus.RECORDED)) { "照片定位狀態不一致" }
        location?.validated()
        return this
    }
}
object PhotoLocationRules {
    const val MAX_AGE_NANOS = 15_000_000_000L
    fun snapshot(location: PhotoLocation?, measuredElapsedNanos: Long, captureElapsedNanos: Long,
                 finePermission: Boolean, coarsePermission: Boolean): LocationSnapshot {
        if(!finePermission) return LocationSnapshot(status=if(coarsePermission) PhotoLocationStatus.APPROXIMATE_ONLY else PhotoLocationStatus.PERMISSION_DENIED)
        if(location==null) return LocationSnapshot(status=PhotoLocationStatus.SEARCHING)
        if(measuredElapsedNanos<=0 || captureElapsedNanos<measuredElapsedNanos || captureElapsedNanos-measuredElapsedNanos>MAX_AGE_NANOS) return LocationSnapshot(status=PhotoLocationStatus.STALE)
        return try { LocationSnapshot(location.validated(),PhotoLocationStatus.RECORDED) }
        catch(_:IllegalArgumentException) { LocationSnapshot(status=PhotoLocationStatus.INACCURATE) }
    }
}
