package com.nqutaoitfteikc.notehub.maps

import com.nqutaoitfteikc.notehub.domain.PhotoMapPoint
import kotlinx.coroutines.ensureActive
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.*
import kotlin.coroutines.coroutineContext

/** One local source and three GPU layers, regardless of photo count. No Marker objects per photo. */
object PhotoMapLayers {
    const val SOURCE="notehub-photos"
    const val CLUSTERS="notehub-photo-clusters"
    const val COUNTS="notehub-photo-counts"
    const val POINTS="notehub-photo-points"
    const val OFFLINE_STYLE="""{"version":8,"sources":{},"layers":[{"id":"local-background","type":"background","paint":{"background-color":"#dce5ed"}}]}"""
    const val STYLE="https://tiles.openfreemap.org/styles/liberty"
    const val CLUSTER_MAX_ZOOM=20
    suspend fun geoJson(points:List<PhotoMapPoint>):String {
        val features=ArrayList<Feature>(points.size)
        points.forEachIndexed { index,p ->
            if(index%256==0) coroutineContext.ensureActive()
            val feature=Feature.fromGeometry(Point.fromLngLat(p.location.longitude,p.location.latitude))
            feature.addStringProperty("photo_id",p.photoId)
            features.add(feature)
        }
        return FeatureCollection.fromFeatures(features).toJson()
    }
    fun install(style:Style,json:String, withLabels:Boolean=true) {
        style.addSource(GeoJsonSource(SOURCE,json,GeoJsonOptions().withCluster(true).withClusterRadius(60).withMaxZoom(22).withClusterMaxZoom(CLUSTER_MAX_ZOOM).withSynchronousUpdate(false)))
        style.addLayer(CircleLayer(CLUSTERS,SOURCE).withFilter(has("point_count")).withProperties(
            circleColor("#255fc4"),circleOpacity(.92f),circleRadius(step(toNumber(get("point_count")),literal(21),stop(10,26),stop(100,32))),circleStrokeWidth(2f),circleStrokeColor("#ffffff")))
        style.addLayer(CircleLayer(POINTS,SOURCE).withFilter(not(has("point_count"))).withProperties(
            circleRadius(8f),circleColor("#088581"),circleStrokeWidth(2f),circleStrokeColor("#ffffff")))
        if(withLabels) style.addLayer(SymbolLayer(COUNTS,SOURCE).withFilter(has("point_count")).withProperties(
            textField(toString(get("point_count_abbreviated"))),textSize(14f),textColor("#ffffff"),textAllowOverlap(true),textIgnorePlacement(true),textFont(arrayOf("Noto Sans Regular"))))
    }
    const val CURRENT="notehub-current-location"
    fun showCurrentLocation(style:Style,location:com.nqutaoitfteikc.notehub.domain.PhotoLocation) {
        val feature=Feature.fromGeometry(Point.fromLngLat(location.longitude,location.latitude))
        val existing=style.getSourceAs<GeoJsonSource>(CURRENT)
        if(existing!=null) existing.setGeoJson(feature)
        else {
            style.addSource(GeoJsonSource(CURRENT,feature))
            style.addLayer(CircleLayer("notehub-current-location-dot",CURRENT).withProperties(
                circleRadius(9f),circleColor("#1976ff"),circleStrokeColor("#ffffff"),circleStrokeWidth(3f)))
        }
    }

    fun clearCurrentLocation(style:Style) {
        if(style.getLayer("notehub-current-location-dot")!=null) style.removeLayer("notehub-current-location-dot")
        if(style.getSource(CURRENT)!=null) style.removeSource(CURRENT)
    }

}
