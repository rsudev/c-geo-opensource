package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.view.MapView;

import android.content.Context;
import android.util.AttributeSet;

public class MfMapView extends MapView {

    public MfMapView(final Context context, final AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public Viewport getViewport() {
        final LatLong center = getModel().mapViewPosition.getCenter();
        return new Viewport(new Geopoint(center.latitude, center.longitude), getLatitudeSpan(), getLongitudeSpan());
    }

    public double getLatitudeSpan() {

        double span = 0;

        final long mapSize = MercatorProjection.getMapSize(getModel().mapViewPosition.getZoomLevel(), getModel().displayModel.getTileSize());
        final Point center = MercatorProjection.getPixelAbsolute(getModel().mapViewPosition.getCenter(), mapSize);

        if (getHeight() > 0) {

            final LatLong low = MercatorProjection.fromPixels(center.x, center.y - getHeight() / 2, mapSize);
            final LatLong high = MercatorProjection.fromPixels(center.x, center.y + getHeight() / 2, mapSize);

            if (low != null && high != null) {
                span = Math.abs(high.latitude - low.latitude);
            }
        }

        return span;
    }

    public double getLongitudeSpan() {

        double span = 0;

        final long mapSize = MercatorProjection.getMapSize(getModel().mapViewPosition.getZoomLevel(), getModel().displayModel.getTileSize());
        final Point center = MercatorProjection.getPixelAbsolute(getModel().mapViewPosition.getCenter(), mapSize);

        if (getWidth() > 0) {
            final LatLong low = MercatorProjection.fromPixels(center.x - getWidth() / 2, center.y, mapSize);
            final LatLong high = MercatorProjection.fromPixels(center.x + getWidth() / 2, center.y, mapSize);

            if (low != null && high != null) {
                span = Math.abs(high.longitude - low.longitude);
            }
        }

        return span;
    }

    public int getMapZoomLevel() {
        return getModel().mapViewPosition.getZoomLevel() - 3;
    }

    public void zoomToSpan(final double latSpan, final double lonSpan) {

        if (latSpan != 0 || lonSpan != 0) {
            // calculate zoomlevel
            final double distDegree = Math.max(latSpan, lonSpan);
            final byte zoomLevel = (byte) Math.floor(Math.log(360.0 / distDegree) / Math.log(2));
            getModel().mapViewPosition.setZoomLevel((byte) (zoomLevel + 1));
        }
    }

}
