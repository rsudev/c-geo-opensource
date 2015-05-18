package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.CGeoMap.MapMode;
import cgeo.geocaching.settings.Settings;

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
        return getModel().mapViewPosition.getZoomLevel() + 3;
    }

    public void setMapZoomLevel(final int zoomLevel) {
        getModel().mapViewPosition.setZoomLevel((byte) (zoomLevel - 3));
    }

    public void zoomToViewport(final Viewport viewport) {

        if (viewport.bottomLeft.equals(viewport.topRight)) {
            setMapZoomLevel(Settings.getMapZoom(MapMode.SINGLE));
        } else {
            final int tileSize = getModel().displayModel.getTileSize();
            final long mapSize = MercatorProjection.getMapSize((byte) 0, tileSize);
            final double dxMax = MercatorProjection.longitudeToPixelX(viewport.getLongitudeMax(), mapSize) / tileSize;
            final double dxMin = MercatorProjection.longitudeToPixelX(viewport.getLongitudeMin(), mapSize) / tileSize;
            final double zoomX = Math.floor(-Math.log(3.8) * Math.log(Math.abs(dxMax - dxMin)) + getWidth() / tileSize);
            final double dyMax = MercatorProjection.longitudeToPixelX(viewport.getLatitudeMax(), mapSize) / tileSize;
            final double dyMin = MercatorProjection.longitudeToPixelX(viewport.getLatitudeMin(), mapSize) / tileSize;
            final double zoomY = Math.floor(-Math.log(3.8) * Math.log(Math.abs(dyMax - dyMin)) + getHeight() / tileSize);
            final byte newZoom = Double.valueOf(Math.min(zoomX, zoomY)).byteValue();
            getModel().mapViewPosition.setZoomLevel(newZoom);
        }
        getModel().mapViewPosition.setCenter(new LatLong(viewport.getCenter().getLatitude(), viewport.getCenter().getLongitude()));
    }
}
