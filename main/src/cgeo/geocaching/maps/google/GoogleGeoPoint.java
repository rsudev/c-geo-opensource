package cgeo.geocaching.maps.google;

import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;

import com.google.android.gms.maps.model.LatLng;

public class GoogleGeoPoint implements GeoPointImpl {

    private final LatLng latLng;

    public GoogleGeoPoint(LatLng latLngIn) {
        latLng = latLngIn;
    }

    public GoogleGeoPoint(double latitude, double longitude) {
        latLng = new LatLng(latitude, longitude);
    }

    @Override
    public Geopoint getCoords() {
        return new Geopoint(getLatitudeE6() / 1e6, getLongitudeE6() / 1e6);
    }

    @Override
    public int getLatitudeE6() {
        return (int) Math.round(latLng.latitude * 1.0e6);
    }

    @Override
    public int getLongitudeE6() {
        return (int) Math.round(latLng.longitude * 1.0e6);
    }

    public LatLng getLatLng() {
        return latLng;
    }
}
