package com.nqutaoitfteikc.notehub.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground camera lifecycle owns this listener. No last-known or background location is used. */
class GpsPhotoLocation(private val context: Context, private val provider: String = LocationManager.GPS_PROVIDER) {
    private val manager=context.getSystemService(LocationManager::class.java)
    private data class Fix(val location: PhotoLocation?, val elapsed: Long, val status: PhotoLocationStatus)
    @Volatile private var fix=Fix(null,0,PhotoLocationStatus.NOT_RECORDED)
    private val mutable=MutableStateFlow(LocationSnapshot())
    val state=mutable.asStateFlow()
    private var listening=false
    private fun granted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    private fun set(value:Fix) { fix=value; mutable.value=LocationSnapshot(value.location.takeIf { value.status==PhotoLocationStatus.RECORDED },value.status) }
    private val listener=object:LocationListener {
        override fun onLocationChanged(location:Location) {
            if(!listening) return
            val point=PhotoLocation(location.latitude,location.longitude,if(location.hasAccuracy()) location.accuracy else Float.NaN,location.time,LocationCompat.isMock(location))
            val snapshot=PhotoLocationRules.snapshot(point,location.elapsedRealtimeNanos,SystemClock.elapsedRealtimeNanos(),granted(Manifest.permission.ACCESS_FINE_LOCATION),granted(Manifest.permission.ACCESS_COARSE_LOCATION))
            set(Fix(snapshot.location,location.elapsedRealtimeNanos,snapshot.status))
        }
        override fun onProviderDisabled(provider:String) { if(listening && provider==this@GpsPhotoLocation.provider) set(Fix(null,0,PhotoLocationStatus.GPS_DISABLED)) }
        override fun onProviderEnabled(provider:String) { if(listening && provider==this@GpsPhotoLocation.provider) set(Fix(null,0,PhotoLocationStatus.SEARCHING)) }
        @Deprecated("舊版 Android 需要此回呼") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) = Unit
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun start() {
        stop()
        if(!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            set(Fix(null,0,if(granted(Manifest.permission.ACCESS_COARSE_LOCATION)) PhotoLocationStatus.APPROXIMATE_ONLY else PhotoLocationStatus.PERMISSION_DENIED)); return
        }
        try {
            if(manager==null || !manager.isProviderEnabled(provider)) { set(Fix(null,0,PhotoLocationStatus.GPS_DISABLED)); return }
            set(Fix(null,0,PhotoLocationStatus.SEARCHING))
            manager.requestLocationUpdates(provider,1000L,0f,listener,Looper.getMainLooper())
            listening=true
        } catch(e:Exception) { Log.e("NoteHubLocation","無法啟動前景 GPS 定位",e); set(Fix(null,0,PhotoLocationStatus.ERROR)) }
    }
    fun stop() {
        val wasListening=listening
        listening=false
        if(wasListening) try { manager?.removeUpdates(listener) } catch(e:Exception) { Log.w("NoteHubLocation","停止 GPS 定位失敗",e) }
        listening=false
        set(Fix(null,0,PhotoLocationStatus.NOT_RECORDED))
    }
    fun snapshot(captureElapsedNanos:Long):LocationSnapshot {
        val current=fix
        if(!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return PhotoLocationRules.snapshot(null,0,captureElapsedNanos,false,granted(Manifest.permission.ACCESS_COARSE_LOCATION))
        if(current.location==null) return LocationSnapshot(status=current.status)
        return PhotoLocationRules.snapshot(current.location,current.elapsed,captureElapsedNanos,true,true)
    }
}
