package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.DataStore;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.MapUtils;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.overlay.Marker;

import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CachesOverlay {

    private final SearchResult search;
    private final MfMapView mapView;
    private final Layer layerAnchor;
    private final List<Layer> layerList = new ArrayList<>();

    public CachesOverlay(final SearchResult search, final MfMapView mapView, final Layer layerAnchor) {
        this.search = search;
        this.mapView = mapView;
        this.layerAnchor = layerAnchor;
        startDisplay();
    }

    public CachesOverlay(final String geocode, final MfMapView mapView, final Layer layerAnchor) {
        this.search = new SearchResult();
        this.search.addGeocode(geocode);
        this.mapView = mapView;
        this.layerAnchor = layerAnchor;
        startDisplay();
    }

    private void startDisplay() {
        new Loader().execute(null, null, null);
    }

    private void fill() {
        try {
            //            showProgressHandler.sendEmptyMessage(SHOW_PROGRESS);
            clearLayers();

            // display caches
            final Set<Geocache> cachesToDisplay = search.getCachesFromSearchResult(LoadFlags.LOAD_WAYPOINTS);

            if (!cachesToDisplay.isEmpty()) {
                // Only show waypoints for single view or setting
                // when less than showWaypointsthreshold Caches shown
                final boolean showWaypoints = cachesToDisplay.size() == 1 || cachesToDisplay.size() < Settings.getWayPointsThreshold();

                for (final Geocache cache : cachesToDisplay) {

                    if (cache == null) {
                        continue;
                    }
                    if (showWaypoints) {
                        final List<Waypoint> waypoints = cache.getWaypoints();
                        for (final Waypoint waypoint : waypoints) {
                            if (waypoint == null || waypoint.getCoords() == null) {
                                continue;
                            }
                            layerList.add(getWaypointItem(waypoint));
                        }
                    }

                    if (cache.getCoords() == null) {
                        continue;
                    }
                    layerList.add(getCacheItem(cache));
                }
            }

            addLayers();

            final Viewport viewport = DataStore.getBounds(search.getGeocodes());

            if (viewport != null) {
                mapView.getModel().mapViewPosition.setCenter(new LatLong(viewport.center.getLatitude(), viewport.center.getLongitude()));
                if (viewport.getLatitudeSpan() != 0 && viewport.getLongitudeSpan() != 0) {
                    mapView.zoomToSpan(viewport.getLatitudeSpan(), viewport.getLongitudeSpan());
                }
            }
            mapView.repaint();
        } finally {
            //            showProgressHandler.sendEmptyMessage(HIDE_PROGRESS);
        }
    }

    public void onDestroy() {
        clearLayers();

    }

    private void addLayers() {
        final Layers layers = this.mapView.getLayerManager().getLayers();
        final int index = layers.indexOf(layerAnchor) + 1;
        layers.addAll(index, layerList);
    }

    private void clearLayers() {
        final Layers layers = this.mapView.getLayerManager().getLayers();

        for (final Layer layer : layerList) {
            layers.remove(layer);
        }

        layerList.clear();
    }

    private static Marker getCacheItem(final Geocache cache) {
        final Geopoint target = cache.getCoords();
        final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getCacheMarker(CgeoApplication.getInstance().getResources(), cache));
        final Marker item = new Marker(new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
        return item;
    }

    private static Marker getWaypointItem(final Waypoint waypoint) {
        final Geopoint target = waypoint.getCoords();
        final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getWaypointMarker(CgeoApplication.getInstance().getResources(), waypoint));
        final Marker item = new Marker(new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
        return item;
    }

    private class Loader extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(final Void... arg0) {
            fill();
            return null;
        }

    }

}
