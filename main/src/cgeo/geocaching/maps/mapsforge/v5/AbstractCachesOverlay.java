package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.DataStore;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.utils.MapUtils;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Layers;

import android.os.Handler;

import java.util.Collection;

public abstract class AbstractCachesOverlay {

    private final MfMapView mapView;
    private final Layer layerAnchor;
    private final GeoitemLayers layerList = new GeoitemLayers();
    private final TapHandler tapHandler;
    private final Handler displayHandler;
    private final Handler showProgressHandler;

    public AbstractCachesOverlay(final MfMapView mapView, final Layer layerAnchor, final TapHandler tapHandler, final Handler displayHandler, final Handler showProgressHandler) {
        this.mapView = mapView;
        this.layerAnchor = layerAnchor;
        this.tapHandler = tapHandler;
        this.displayHandler = displayHandler;
        this.showProgressHandler = showProgressHandler;
    }

    public void onDestroy() {
        clearLayers();
    }

    public int getVisibleItemsCount() {
        return mapView.getViewport().count(DataStore.loadCaches(getGeocodes(), LoadFlags.LOAD_CACHE_OR_DB));
    }

    public int getItemsCount() {
        return layerList.size();
    }

    protected final void addItem(final Geocache cache) {
        layerList.add(getCacheItem(cache, this.tapHandler));
    }

    protected final void addItem(final Waypoint waypoint) {
        final GeoitemLayer waypointItem = getWaypointItem(waypoint, this.tapHandler);
        if (waypointItem != null) {
            layerList.add(waypointItem);
        }
    }

    protected void addLayers() {
        final Layers layers = this.mapView.getLayerManager().getLayers();
        final int index = layers.indexOf(layerAnchor) + 1;
        layers.addAll(index, layerList.getAsLayers());
    }

    protected Collection<String> getGeocodes() {
        return layerList.getGeocodes();
    }

    protected Viewport getViewport() {
        return this.mapView.getViewport();
    }

    protected int getMapZoomLevel() {
        return this.mapView.getMapZoomLevel();
    }

    protected void showProgress() {
        showProgressHandler.sendEmptyMessage(NewMap.SHOW_PROGRESS);
    }

    protected void hideProgress() {
        showProgressHandler.sendEmptyMessage(NewMap.HIDE_PROGRESS);
    }

    protected void repaint() {
        displayHandler.sendEmptyMessage(NewMap.INVALIDATE_MAP);
        displayHandler.sendEmptyMessage(NewMap.UPDATE_TITLE);
    }

    protected void clearLayers() {
        final Layers layers = this.mapView.getLayerManager().getLayers();

        for (final Layer layer : layerList) {
            layers.remove(layer);
        }

        layerList.clear();
    }

    protected void syncLayers(final Collection<String> removeCodes, final Collection<String> newCodes) {
        final Layers layers = this.mapView.getLayerManager().getLayers();
        for (final String code : removeCodes) {
            final GeoitemLayer item = layerList.getItem(code);
            layers.remove(item);
            layerList.remove(item);
        }
        final int index = layers.indexOf(layerAnchor) + 1;
        layers.addAll(index, layerList.getMatchingLayers(newCodes));
    }

    private static GeoitemLayer getCacheItem(final Geocache cache, final TapHandler tapHandler) {
        final Geopoint target = cache.getCoords();
        final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getCacheMarker(CgeoApplication.getInstance().getResources(), cache));
        final GeoitemLayer item = new GeoitemLayer(cache.getGeoitemRef(), tapHandler, new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
        return item;
    }

    private static GeoitemLayer getWaypointItem(final Waypoint waypoint, final TapHandler tapHandler) {
        final Geopoint target = waypoint.getCoords();

        if (target != null) {
            final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getWaypointMarker(CgeoApplication.getInstance().getResources(), waypoint));
            final GeoitemLayer item = new GeoitemLayer(waypoint.getGeoitemRef(), tapHandler, new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
            return item;
        }

        return null;
    }
}