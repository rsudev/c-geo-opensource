package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.DataStore;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.gc.GCLogin;
import cgeo.geocaching.connector.gc.MapTokens;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.RemoveFlag;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MapUtils;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.Layers;

import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LiveCachesOverlay {

    private final Set<Geocache> caches = new HashSet<>();
    private final MfMapView mapView;
    private final Layer layerAnchor;
    private final GeoitemLayers layerList = new GeoitemLayers();
    private final TapHandler tapHandler;
    private final Subscription timer;
    private boolean downloading = false;
    public long loadThreadRun = -1;
    private MapTokens tokens;

    public LiveCachesOverlay(final MfMapView mapView, final Layer layerAnchor, final TapHandler tapHandler) {
        this.mapView = mapView;
        this.layerAnchor = layerAnchor;
        this.tapHandler = tapHandler;
        this.timer = startTimer();
    }

    private Subscription startTimer() {
        return Schedulers.newThread().createWorker().schedulePeriodically(new LoadTimerAction(this), 0, 250, TimeUnit.MILLISECONDS);
    }

    private static final class LoadTimerAction implements Action0 {

        @NonNull private final WeakReference<LiveCachesOverlay> overlayRef;
        private int previousZoom = -100;
        private Viewport previousViewport;

        public LoadTimerAction(@NonNull final LiveCachesOverlay overlay) {
            this.overlayRef = new WeakReference<>(overlay);
        }

        @Override
        public void call() {
            final LiveCachesOverlay overlay = overlayRef.get();
            if (overlay == null || overlay.downloading) {
                return;
            }
            try {
                // get current viewport
                final Viewport viewportNow = overlay.mapView.getViewport();
                // Since zoomNow is used only for local comparison purposes,
                // it is ok to use the Google Maps compatible zoom level of OSM Maps
                final int zoomNow = overlay.mapView.getMapZoomLevel();

                // check if map moved or zoomed
                //TODO Portree Use Rectangle inside with bigger search window. That will stop reloading on every move
                final boolean moved = (previousViewport == null) || zoomNow != previousZoom ||
                        (mapMoved(previousViewport, viewportNow) || !previousViewport.includes(viewportNow));

                // update title on any change
                if (moved || !viewportNow.equals(previousViewport)) {
//                    map.displayHandler.sendEmptyMessage(UPDATE_TITLE);
                }
                previousZoom = zoomNow;

                // save new values
                if (moved) {
                    final long currentTime = System.currentTimeMillis();

                    if (1000 < (currentTime - overlay.loadThreadRun )) {
                        overlay.downloading = true;
                        previousViewport = viewportNow;
                        overlay.download();
                        overlay.downloading = false;
                    }
                }
            } catch (final Exception e) {
                Log.w("CGeoMap.startLoadtimer.start", e);
            } finally {
                overlay.downloading = false;
            }
        }
    }

    private void download() {
        try {
            //            showProgressHandler.sendEmptyMessage(SHOW_PROGRESS); // show progress
            if (Settings.isGCConnectorActive()) {
                if (tokens == null) {
                    tokens = GCLogin.getInstance().getMapTokens();
                    if (StringUtils.isEmpty(tokens.getUserSession()) || StringUtils.isEmpty(tokens.getSessionToken())) {
                        tokens = null;
                        //                        if (!noMapTokenShowed) {
                        //                            ActivityMixin.showToast(activity, res.getString(R.string.map_token_err));
                        //                            noMapTokenShowed = true;
                        //                        }
                    }
                }
            }
            final SearchResult searchResult = ConnectorFactory.searchByViewport(mapView.getViewport().resize(0.8), tokens);

            final Set<Geocache> result = searchResult.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB);
            filter(result);
            // update the caches
            // first remove filtered out
            final Set<String> filteredCodes = searchResult.getFilteredGeocodes();
            Log.d("Filtering out " + filteredCodes.size() + " caches: " + filteredCodes.toString());
            DataStore.removeCaches(filteredCodes, EnumSet.of(RemoveFlag.CACHE));
            // new collection type needs to remove first to refresh
            caches.removeAll(result);
            caches.addAll(result);

            //render
            fill();

        } finally {
            //            showProgressHandler.sendEmptyMessage(HIDE_PROGRESS); // hide progress
        }
    }


    private void fill() {
        try {
            //            showProgressHandler.sendEmptyMessage(SHOW_PROGRESS);
            final Collection<String> removeCodes = layerList.getGeocodes();
            final Collection<String> newCodes = new HashSet<>();

            // display caches
            final Set<Geocache> cachesToDisplay = caches;

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
                            if (removeCodes.contains(waypoint.getGeocode())) {
                                removeCodes.remove(waypoint.getGeocode());
                            } else {
                                layerList.add(getWaypointItem(waypoint, this.tapHandler));
                                newCodes.add(waypoint.getGeocode());
                            }
                        }
                    }

                    if (cache.getCoords() == null) {
                        continue;
                    }
                    if (removeCodes.contains(cache.getGeocode())) {
                        removeCodes.remove(cache.getGeocode());
                    } else {
                        layerList.add(getCacheItem(cache, this.tapHandler));
                        newCodes.add(cache.getGeocode());
                    }
                }
            }

            syncLayers(removeCodes, newCodes);

            mapView.repaint();
        } finally {
            //            showProgressHandler.sendEmptyMessage(HIDE_PROGRESS);
        }
    }

    public void onDestroy() {
        timer.unsubscribe();

        clearLayers();
    }

    private void syncLayers(final Collection<String> removeCodes, final Collection<String> newCodes) {
        final Layers layers = this.mapView.getLayerManager().getLayers();
        for (final String code : removeCodes) {
            final GeoitemLayer item = layerList.getItem(code);
            layers.remove(item);
            layerList.remove(item);
        }
        final int index = layers.indexOf(layerAnchor) + 1;
        layers.addAll(index, layerList.getMatchingLayers(newCodes));
    }

    private void clearLayers() {
        final Layers layers = this.mapView.getLayerManager().getLayers();

        for (final Layer layer : layerList) {
            layers.remove(layer);
        }

        layerList.clear();
    }

    private static GeoitemLayer getCacheItem(final Geocache cache, final TapHandler tapHandler) {
        final Geopoint target = cache.getCoords();
        final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getCacheMarker(CgeoApplication.getInstance().getResources(), cache));
        final GeoitemLayer item = new GeoitemLayer(cache.getGeocode(), tapHandler, new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
        return item;
    }

    private static GeoitemLayer getWaypointItem(final Waypoint waypoint, final TapHandler tapHandler) {
        final Geopoint target = waypoint.getCoords();
        final Bitmap marker = AndroidGraphicFactory.convertToBitmap(MapUtils.getWaypointMarker(CgeoApplication.getInstance().getResources(), waypoint));
        final GeoitemLayer item = new GeoitemLayer(waypoint.getGeocode(), tapHandler, new LatLong(target.getLatitude(), target.getLongitude()), marker, 0, -marker.getHeight() / 2);
        return item;
    }

    private static synchronized void filter(final Collection<Geocache> caches) {
        final boolean excludeMine = Settings.isExcludeMyCaches();
        final boolean excludeDisabled = Settings.isExcludeDisabledCaches();

        final List<Geocache> removeList = new ArrayList<>();
        for (final Geocache cache : caches) {
            if ((excludeMine && cache.isFound()) || (excludeMine && cache.isOwner()) || (excludeDisabled && cache.isDisabled()) || (excludeDisabled && cache.isArchived())) {
                removeList.add(cache);
            }
        }
        caches.removeAll(removeList);
    }

    private static boolean mapMoved(final Viewport referenceViewport, final Viewport newViewport) {
        return Math.abs(newViewport.getLatitudeSpan() - referenceViewport.getLatitudeSpan()) > 50e-6 ||
                Math.abs(newViewport.getLongitudeSpan() - referenceViewport.getLongitudeSpan()) > 50e-6 ||
                Math.abs(newViewport.center.getLatitude() - referenceViewport.center.getLatitude()) > referenceViewport.getLatitudeSpan() / 4 ||
                Math.abs(newViewport.center.getLongitude() - referenceViewport.center.getLongitude()) > referenceViewport.getLongitudeSpan() / 4;
    }

}
