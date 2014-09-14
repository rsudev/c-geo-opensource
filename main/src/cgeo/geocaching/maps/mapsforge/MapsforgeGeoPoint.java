package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;

import org.mapsforge.core.model.LatLong;

public class MapsforgeGeoPoint extends LatLong implements GeoPointImpl {

    private static final long serialVersionUID = 1L;

    public MapsforgeGeoPoint(final double latitude, final double longitude) {
        super(latitude, longitude);
    }

    public MapsforgeGeoPoint(final int latitudeE6, final int longitudeE6) {
        super(latitudeE6 * 1.0 / 1e6, longitudeE6 * 1.0 / 1e6);
    }

    @Override
    public Geopoint getCoords() {
        return new Geopoint(getLatitudeE6() / 1e6, getLongitudeE6() / 1e6);
    }

    @Override
    public int getLatitudeE6() {
        return (int) Math.round(latitude * 1e6);
    }
    @Override
    public int getLongitudeE6() {
        return (int) Math.round(longitude * 1e6);
    }
}
