package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.maps.CachesOverlay;
import cgeo.geocaching.maps.interfaces.ItemizedOverlayImpl;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;
import cgeo.geocaching.maps.interfaces.MapViewImpl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MapsforgeCacheOverlay /* extends ItemizedOverlay<MapsforgeCacheOverlayItem> */implements ItemizedOverlayImpl {

    private final CachesOverlay base;
    private final Lock lock = new ReentrantLock();

    public MapsforgeCacheOverlay(final Context contextIn, final Drawable markerIn) {
        //        super(boundCenterBottom(markerIn));
        base = new CachesOverlay(this, contextIn);
    }

    @Override
    public CachesOverlay getBase() {
        return base;
    }

    protected MapsforgeCacheOverlayItem createItem(final int i) {
        if (base == null) {
            return null;
        }

        return (MapsforgeCacheOverlayItem) base.createItem(i);
    }

    public int size() {
        if (base == null) {
            return 0;
        }

        return base.size();
    }

    protected boolean onTap(final int arg0) {
        if (base == null) {
            return false;
        }

        return base.onTap(arg0);
    }

    protected void drawOverlayBitmap(final Canvas canvas, final Point drawPosition,/*
                                                                                    * Projection projection,
                                                                                    */final byte drawZoomLevel) {
        base.drawOverlayBitmap(canvas, drawPosition, new MapsforgeMapProjection(), drawZoomLevel);
    }

    @Override
    public void superPopulate() {
        //        populate();
    }

    @Override
    public Drawable superBoundCenterBottom(final Drawable marker) {
        return marker; // ItemizedOverlay.boundCenterBottom(marker);
    }

    @Override
    public void superSetLastFocusedItemIndex(final int i) {
        // nothing to do
    }

    @Override
    public boolean superOnTap(final int index) {
        return false; //super.onTap(index);
    }

    @Override
    public void superDraw(final Canvas canvas, final MapViewImpl mapView, final boolean shadow) {
        // nothing to do here...
    }

    @Override
    public void superDrawOverlayBitmap(final Canvas canvas, final Point drawPosition,
            final MapProjectionImpl projection, final byte drawZoomLevel) {
        //        super.drawOverlayBitmap(canvas, drawPosition, (Projection) projection.getImpl(), drawZoomLevel);
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    @Override
    public MapViewImpl getMapViewImpl() {
        return null; // (MapViewImpl) internalMapView;
    }

}
