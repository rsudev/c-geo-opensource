package cgeo.geocaching.maps.google;

import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;

import com.google.android.gms.maps.Projection;

import android.graphics.Point;

public class GoogleMapProjection implements MapProjectionImpl {

    private Projection projection;

    public GoogleMapProjection(Projection projectionIn) {
        projection = projectionIn;
    }

    @Override
    public void toPixels(GeoPointImpl leftGeo, Point left) {
        Point temp = projection.toScreenLocation(((GoogleGeoPoint) leftGeo).getLatLng());
        left.x = temp.x;
        left.y = temp.y;
    }

    @Override
    public Object getImpl() {
        return projection;
    }

}
