package com.nqutaoitfteikc.notehub.privacy

import java.net.URI
import android.content.Context
import org.maplibre.android.storage.FileSource

/** Resource-level boundary, NOT an OS firewall. Local overlay GeoJSON is never a remote URL. */
object MapResourceGate {
    const val BLOCKED="asset://privacy-blocked.json"
    @Volatile private var online=false
    fun permitted(url: String, enabled: Boolean): Boolean = try {
        val uri=URI(url)
        enabled && uri.scheme=="https" && uri.host=="tiles.openfreemap.org" && uri.userInfo==null && uri.port in listOf(-1,443) && uri.fragment==null
    } catch(_:Exception) { false }
    fun configure(context: Context, enabled: Boolean) {
        online=enabled
        FileSource.getInstance(context).setResourceTransform { _,url -> if(permitted(url,online)) url else BLOCKED }
    }
}
