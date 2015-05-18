package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.utils.MapUtils;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Layers;

import java.util.Collection;

public abstract class AbstractCachesOverlay {

    private final MfMapView mapView;
    private final Layer layerAnchor;
    private final GeoitemLayers layerList = new GeoitemLayers();
    private final TapHandler tapHandler;

    public AbstractCachesOverlay(final MfMapView mapView, final Layer layerAnchor, final TapHandler tapHandler) {
        this.mapView = mapView;
        this.layerAnchor = layerAnchor;
        this.tapHandler = tapHandler;
    }

    public void onDestroy() {
        clearLayers();
    }

    protected void addItem(final Geocache cache) {
        layerList.add(getCacheItem(cache, this.tapHandler));
    }

    protected void addItem(final Waypoint waypoint) {
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

    protected void repaint() {
        mapView.repaint();
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