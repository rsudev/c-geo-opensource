package cgeo.geocaching.maps.google;

import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.Viewport;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapControllerImpl;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import android.graphics.Point;

public class GoogleMapController implements MapControllerImpl {

    private GoogleMap map;

    public GoogleMapController(GoogleMap mapIn) {
        map = mapIn;
    }

    @Override
    public void animateTo(GeoPointImpl geoPoint) {
        map.animateCamera(CameraUpdateFactory.newLatLng(castToGeoPointImpl(geoPoint)));
    }

    private static LatLng castToGeoPointImpl(GeoPointImpl geoPoint) {
        assert geoPoint instanceof GoogleGeoPoint;
        return ((GoogleGeoPoint) geoPoint).getLatLng();
    }

    @Override
    public void setCenter(GeoPointImpl geoPoint) {
        map.moveCamera(CameraUpdateFactory.newLatLng(castToGeoPointImpl(geoPoint)));
    }

    @Override
    public void setZoom(int mapzoom) {
        map.animateCamera(CameraUpdateFactory.zoomTo(mapzoom));
    }

    @Override
    public void zoomToSpan(int latSpanE6, int lonSpanE6) {
        if (latSpanE6 != 0 && lonSpanE6 != 0) {
            // calculate zoomlevel
            int distDegree = Math.max(latSpanE6, lonSpanE6);
            int zoomLevel = (int) Math.floor(Math.log(360.0 * 1e6 / distDegree) / Math.log(2));
            setZoom(zoomLevel + 1);
        }
    }

    public void zoomInFixing(int screenX, int screenY) {
        Point fixPoint = new Point(screenX, screenY);
        map.animateCamera(CameraUpdateFactory.zoomBy(1, fixPoint));
    }

    public LatLng getMapCenter() {
        return map.getCameraPosition().target;
    }

    public Viewport getViewport() {
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        return new Viewport(new Geopoint(bounds.getCenter().latitude, bounds.getCenter().longitude), bounds.northeast.latitude - bounds.southwest.latitude, bounds.northeast.longitude - bounds.southwest.longitude);
    }

    public int getLatitudeSpan() {
        return (int) Math.round(getViewport().getLatitudeSpan() * 1e6);
    }

    public int getLongitudeSpan() {
        return (int) Math.round(getViewport().getLongitudeSpan() * 1e6);
    }
}
