package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.maps.mapsforge.v6.MfMapView;
import cgeo.geocaching.maps.mapsforge.v6.NewMap;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.mapsforge.map.layer.LayerManager;

public class CachesBundle {

    private static final int WP_SEPERATOR = 0;
    private static final int BASE_SEPARATOR = 1;
    private static final int STORED_SEPARATOR = 2;
    private static final int LIVE_SEPARATOR = 3;

    private static final int WP_OVERLAY_ID = 0;
    private static final int BASE_OVERLAY_ID = 1;
    private static final int STORED_OVERLAY_ID = 2;
    private static final int LIVE_OVERLAY_ID = 3;

    private final MfMapView mapView;
    private final MapHandlers mapHandlers;

    private static final int INITIAL_ENTRY_COUNT = 200;
    private final Set<GeoEntry> geoEntries = Collections.synchronizedSet(new GeoEntrySet(INITIAL_ENTRY_COUNT));

    private WaypointsOverlay wpOverlay;
    private AbstractCachesOverlay baseOverlay;
    private AbstractCachesOverlay storedOverlay;
    private LiveCachesOverlay liveOverlay;
    private final List<SeparatorLayer> separators = new ArrayList<>();

    private final Disposable timer;

    /**
     * Base initialization without any caches up-front
     *
     * @param mapView     the map view this bundle is displayed on
     * @param mapHandlers the handlers of the map to send events to
     */
    public CachesBundle(final MfMapView mapView, final MapHandlers mapHandlers) {
        this.mapView = mapView;
        this.mapHandlers = mapHandlers;

        // prepare separators
        final SeparatorLayer separator1 = new SeparatorLayer();
        this.separators.add(separator1);
        this.mapView.getLayerManager().getLayers().add(separator1);
        final SeparatorLayer separator2 = new SeparatorLayer();
        this.separators.add(separator2);
        this.mapView.getLayerManager().getLayers().add(separator2);
        final SeparatorLayer separator3 = new SeparatorLayer();
        this.separators.add(separator3);
        this.mapView.getLayerManager().getLayers().add(separator3);
        final SeparatorLayer separator4 = new SeparatorLayer();
        this.separators.add(separator4);
        this.mapView.getLayerManager().getLayers().add(separator4);

        this.wpOverlay = new WaypointsOverlay(WP_OVERLAY_ID, this.geoEntries, this, separators.get(WP_SEPERATOR), this.mapHandlers);

        this.timer = startTimer();
    }

    /**
     * Initialization with search result (nearby, list)
     *
     * @param search      the SearchResult to display through this bundle
     * @param mapView     the map view this bundle is displayed on
     * @param mapHandlers the handlers of the map to send events to
     */
    public CachesBundle(final SearchResult search, final MfMapView mapView, final MapHandlers mapHandlers) {
        this(mapView, mapHandlers);
        this.baseOverlay = new CachesOverlay(search, BASE_OVERLAY_ID, this.geoEntries, this, separators.get(BASE_SEPARATOR), this.mapHandlers);
    }

    /**
     * Initialization with single cache
     *
     * @param geocode     the geocode for single cache display through this bundle
     * @param mapView     the map view this bundle is displayed on
     * @param mapHandlers the handlers of the map to send events to
     */
    public CachesBundle(final String geocode, final MfMapView mapView, final MapHandlers mapHandlers) {
        this(mapView, mapHandlers);
        this.baseOverlay = new CachesOverlay(geocode, BASE_OVERLAY_ID, this.geoEntries, this, separators.get(BASE_SEPARATOR), this.mapHandlers);
    }

    /**
     * Initialization with single waypoint
     *
     * @param coords       coordinates for single waypoint to display through this bundle
     * @param waypointType type for single waypoint to display through this bundle
     * @param mapView      the map view this bundle is displayed on
     * @param mapHandlers  the handlers of the map to send events to
     */
    public CachesBundle(final Geopoint coords, final WaypointType waypointType, final MfMapView mapView, final MapHandlers mapHandlers) {
        this(mapView, mapHandlers);
        this.baseOverlay = new SinglePointOverlay(coords, waypointType, BASE_OVERLAY_ID, this.geoEntries, this, separators.get(BASE_SEPARATOR), this.mapHandlers);
    }

    public void handleLiveLayers(final boolean enable) {
        if (enable) {
            if (this.liveOverlay == null) {
                final SeparatorLayer separator2 = this.separators.get(LIVE_SEPARATOR);
                this.liveOverlay = new LiveCachesOverlay(LIVE_OVERLAY_ID, this.geoEntries, this, separator2, this.mapHandlers);
            }
        } else {
            // Disable only download, keep stored caches
            if (this.liveOverlay != null) {
                this.liveOverlay.onDestroy();
                this.liveOverlay = null;
            }
        }
    }

    /**
     * Enables the stored cache layer. No disabling again!
     *
     * @param enable true - enable stored layer, false - leave untouched
     */
    public void enableStoredLayers(final boolean enable) {
        if (!enable || this.storedOverlay != null) {
            return;
        }

        final SeparatorLayer separator1 = this.separators.get(STORED_SEPARATOR);
        this.storedOverlay = new StoredCachesOverlay(STORED_OVERLAY_ID, this.geoEntries, this, separator1, this.mapHandlers);
    }

    private Disposable startTimer() {
        return Schedulers.newThread().schedulePeriodicallyDirect(new LoadTimerAction(this), 0, 250, TimeUnit.MILLISECONDS);
    }

    private static final class LoadTimerAction implements Runnable {

        @NonNull
        private final WeakReference<CachesBundle> bundleRef;
        private int previousZoom = -100;
        private Viewport previousViewport;

        LoadTimerAction(@NonNull final CachesBundle bundle) {
            this.bundleRef = new WeakReference<>(bundle);
        }

        @Override
        public void run() {
            final CachesBundle bundle = bundleRef.get();
            if (bundle == null) {
                return;
            }
            try {
                // get current viewport
                final Viewport viewportNow = bundle.getViewport();
                // Since zoomNow is used only for local comparison purposes,
                // it is ok to use the Google Maps compatible zoom level of OSM Maps
                final int zoomNow = bundle.getMapZoomLevel();

                // check if map moved or zoomed
                //TODO Portree Use Rectangle inside with bigger search window. That will stop reloading on every move
                final boolean moved = bundle.isInvalidated() || previousViewport == null || zoomNow != previousZoom ||
                        mapMoved(previousViewport, viewportNow);

                // save new values
                if (moved) {

                    previousZoom = zoomNow;
                    previousViewport = viewportNow;
                    // load overlays
                    if (bundle.baseOverlay != null) {
                        bundle.baseOverlay.load();
                    }
                    if (bundle.storedOverlay != null) {
                        bundle.storedOverlay.load();
                    }
                    if (bundle.liveOverlay != null) {
                        bundle.liveOverlay.load();
                    }
                    // filter bottom up
                    if (bundle.baseOverlay != null) {
                        bundle.baseOverlay.update();
                    }
                    if (bundle.storedOverlay != null) {
                        bundle.storedOverlay.update();
                    }
                    if (bundle.liveOverlay != null) {
                        bundle.liveOverlay.update();
                    }
                    // possibly load wps
                    if (bundle.getVisibleCachesCount() < Settings.getWayPointsThreshold()) {
                        Collection<String> baseCodes = Collections.EMPTY_LIST;
                        if (bundle.baseOverlay != null) {
                            baseCodes = bundle.baseOverlay.getGeocodes();
                        }
                        bundle.wpOverlay.showWaypoints(baseCodes, bundle.storedOverlay != null);
                    } else {
                        bundle.wpOverlay.hideWaypoints();
                    }
                    // paint
                    bundle.repaint();
                    bundle.refreshed();
                } else if (!previousViewport.equals(viewportNow)) {
                    bundle.updateTitle();
                }
            } catch (final Exception e) {
                Log.w("CachesBundle.LoadTimerAction.run", e);
            }
        }
    }

    private void refreshed() {
        if (baseOverlay != null) {
            baseOverlay.refreshed();
        }
        if (storedOverlay != null) {
            storedOverlay.refreshed();
        }
        if (liveOverlay != null) {
            liveOverlay.refreshed();
        }
    }

    private void repaint() {
        mapHandlers.sendEmptyDisplayMessage(NewMap.INVALIDATE_MAP);
        mapHandlers.sendEmptyDisplayMessage(NewMap.UPDATE_TITLE);
    }

    private void updateTitle() {
        mapHandlers.sendEmptyDisplayMessage(NewMap.UPDATE_TITLE);
    }

    public void onDestroy() {
        timer.dispose();

        if (this.baseOverlay != null) {
            this.baseOverlay.onDestroy();
            this.baseOverlay = null;
        }
        if (this.storedOverlay != null) {
            this.storedOverlay.onDestroy();
            this.storedOverlay = null;
        }
        if (this.liveOverlay != null) {
            this.liveOverlay.onDestroy();
            this.liveOverlay = null;
        }
        for (final SeparatorLayer layer : this.separators) {
            this.mapView.getLayerManager().getLayers().remove(layer);
        }
        this.separators.clear();
    }

    public int getVisibleCachesCount() {

        int result = 0;

        if (this.baseOverlay != null) {
            result += this.baseOverlay.getVisibleCachesCount();
        }
        if (this.storedOverlay != null) {
            result += this.storedOverlay.getVisibleCachesCount();
        }
        if (this.liveOverlay != null) {
            result += this.liveOverlay.getVisibleCachesCount();
        }

        return result;
    }

    public Set<String> getVisibleCacheGeocodes() {

        final Set<String> result = new HashSet<>();

        if (this.baseOverlay != null) {
            result.addAll(this.baseOverlay.getVisibleCacheGeocodes());
        }
        if (this.liveOverlay != null) {
            result.addAll(this.liveOverlay.getVisibleCacheGeocodes());
        }
        if (this.storedOverlay != null) {
            result.addAll(this.storedOverlay.getVisibleCacheGeocodes());
        }

        return result;
    }

    public int getCachesCount() {

        int result = 0;

        if (baseOverlay != null) {
            result += baseOverlay.getCachesCount();
        }
        if (storedOverlay != null) {
            result += storedOverlay.getCachesCount();
        }
        if (liveOverlay != null) {
            result += liveOverlay.getCachesCount();
        }

        return result;
    }

    private boolean isInvalidated() {
        if (storedOverlay != null && storedOverlay.isInvalidated()) {
            return true;
        }
        if (liveOverlay != null && liveOverlay.isInvalidated()) {
            return true;
        }
        return false;
    }

    public void invalidate() {
        if (storedOverlay != null) {
            storedOverlay.invalidate();
        }
        if (liveOverlay != null) {
            liveOverlay.invalidate();
        }
    }

    public void invalidate(final Collection<String> geocodes) {
        if (storedOverlay != null) {
            storedOverlay.invalidate(geocodes);
        }
        if (liveOverlay != null) {
            liveOverlay.invalidate(geocodes);
        }
    }

    public boolean isDownloading() {
        return liveOverlay != null && liveOverlay.isDownloading();
    }

    Viewport getViewport() {
        return mapView.getViewport();
    }

    int getMapZoomLevel() {
        return mapView.getMapZoomLevel();
    }

    LayerManager getLayerManager() {
        return mapView.getLayerManager();
    }

    static boolean mapMoved(final Viewport referenceViewport, final Viewport newViewport) {
        return Math.abs(newViewport.getLatitudeSpan() - referenceViewport.getLatitudeSpan()) > 50e-6 || Math.abs(newViewport.getLongitudeSpan() - referenceViewport.getLongitudeSpan()) > 50e-6 || Math.abs(newViewport.center.getLatitude() - referenceViewport.center.getLatitude()) > referenceViewport.getLatitudeSpan() / 4 || Math.abs(newViewport.center.getLongitude() - referenceViewport.center.getLongitude()) > referenceViewport.getLongitudeSpan() / 4;
    }
}
