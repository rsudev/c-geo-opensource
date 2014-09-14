package cgeo.geocaching.maps;

import cgeo.geocaching.maps.interfaces.GeneralOverlay;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;
import cgeo.geocaching.maps.interfaces.MapViewImpl;
import cgeo.geocaching.maps.interfaces.OverlayImpl;

import android.graphics.Canvas;
import android.graphics.Point;
import android.location.Location;

import java.util.ArrayList;

public class PositionAndScaleOverlay implements GeneralOverlay {
    private OverlayImpl ovlImpl = null;

    PositionDrawer positionDrawer = null;
    ScaleDrawer scaleDrawer = null;

    public PositionAndScaleOverlay(final OverlayImpl ovlImpl) {
        this.ovlImpl = ovlImpl;
        positionDrawer = new PositionDrawer();
        scaleDrawer = new ScaleDrawer();
    }

    public void setCoordinates(final Location coordinatesIn) {
        positionDrawer.setCoordinates(coordinatesIn);
    }

    public Location getCoordinates() {
        return positionDrawer.getCoordinates();
    }

    public void setHeading(final float bearingNow) {
        positionDrawer.setHeading(bearingNow);
    }

    public float getHeading() {
        return positionDrawer.getHeading();
    }

    @Override
    public void drawOverlayBitmap(final Canvas canvas, final Point drawPosition,
            final MapProjectionImpl projection, final byte drawZoomLevel) {

        drawInternal(canvas, projection, getOverlayImpl().getMapViewImpl());
    }

    @Override
    public void draw(final Canvas canvas, final MapViewImpl mapView, final boolean shadow) {

        drawInternal(canvas, mapView.getMapProjection(), mapView);
    }

    private void drawInternal(final Canvas canvas, final MapProjectionImpl projection, final MapViewImpl mapView) {
        //        positionDrawer.drawPosition(canvas, projection);
        //        scaleDrawer.drawScale(canvas, mapView);
    }

    @Override
    public OverlayImpl getOverlayImpl() {
        return this.ovlImpl;
    }

    public ArrayList<Location> getHistory() {
        return positionDrawer.getHistory();
    }

    public void setHistory(final ArrayList<Location> history) {
        positionDrawer.setHistory(history);
    }
}
