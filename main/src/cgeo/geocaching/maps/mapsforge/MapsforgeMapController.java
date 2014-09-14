package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapControllerImpl;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.model.Model;

public class MapsforgeMapController implements MapControllerImpl {

    private final Model mapController;
    private final int maxZoomLevel;

    public MapsforgeMapController(final Model mapControllerIn, final int maxZoomLevelIn) {
        mapController = mapControllerIn;
        maxZoomLevel = maxZoomLevelIn;
    }

    @Override
    public void animateTo(final GeoPointImpl geoPoint) {
        mapController.mapViewPosition.animateTo(castToGeoPoint(geoPoint));
    }

    private static LatLong castToGeoPoint(final GeoPointImpl geoPoint) {
        assert geoPoint instanceof LatLong;
        return (LatLong) geoPoint;
    }

    @Override
    public void setCenter(final GeoPointImpl geoPoint) {
        mapController.mapViewPosition.setCenter(castToGeoPoint(geoPoint));
    }

    /**
     * Set the map zoom level to mapzoom-1 or maxZoomLevel, whichever is least
     * mapzoom-1 is used to be compatible with Google Maps zoom levels
     */
    @Override
    public void setZoom(final int mapzoom) {
        // Google Maps and OSM Maps use different zoom levels for the same view.
        // All OSM Maps zoom levels are offset by 1 so they match Google Maps.
        mapController.mapViewPosition.setZoomLevel((byte) Math.min(mapzoom - 1, maxZoomLevel));
    }

    @Override
    public void zoomToSpan(final int latSpanE6, final int lonSpanE6) {

        if (latSpanE6 != 0 && lonSpanE6 != 0) {
            // calculate zoomlevel
            final int distDegree = Math.max(latSpanE6, lonSpanE6);
            final int zoomLevel = (int) Math.floor(Math.log(360.0 * 1e6 / distDegree) / Math.log(2));
            setZoom(zoomLevel + 1);
        }
    }
}
