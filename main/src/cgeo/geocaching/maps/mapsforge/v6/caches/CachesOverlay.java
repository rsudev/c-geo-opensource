package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.mapsforge.map.layer.Layer;

public class CachesOverlay extends AbstractCachesOverlay {

    private final SearchResult search;
    private final Disposable timer;
    private boolean showWaypoints = false;
    private boolean firstRun = true;
    private boolean updating = false;

    CachesOverlay(final SearchResult search, final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer anchorLayer, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, anchorLayer, mapHandlers);

        this.search = search;
        this.timer = startTimer();
    }

    CachesOverlay(final String geocode, final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer layerAnchor, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, layerAnchor, mapHandlers);

        this.search = new SearchResult();
        this.search.addGeocode(geocode);
        this.timer = startTimer();
    }

    private Disposable startTimer() {
        return Schedulers.newThread().schedulePeriodicallyDirect(new CachesOverlay.LoadTimerAction(this), 0, 250, TimeUnit.MILLISECONDS);
    }

    private static final class LoadTimerAction implements Runnable {

        @NonNull
        private final WeakReference<CachesOverlay> overlayRef;
        private Viewport previousViewport;

        LoadTimerAction(@NonNull final CachesOverlay overlay) {
            this.overlayRef = new WeakReference<>(overlay);
        }

        @Override
        public void run() {
            final CachesOverlay overlay = overlayRef.get();
            if (overlay == null || overlay.updating) {
                return;
            }
            overlay.updating = true;
            try {
                // Initially bring the main list in
                if (overlay.firstRun) {
                    final Set<Geocache> cachesToDisplay = overlay.search.getCachesFromSearchResult(LoadFlags.LOAD_WAYPOINTS);
                    overlay.display(cachesToDisplay);
                    overlay.firstRun = false;
                }

                // get current viewport
                final Viewport viewportNow = overlay.getViewport();

                // Switch waypoints on or off depending on visibility. Leave them always enabled for single cache views
                final boolean showWaypointsNow = overlay.search.getCount() > 1 ? overlay.getAllVisibleCachesCount() < Settings.getWayPointsThreshold() : true;

                if (showWaypointsNow != overlay.showWaypoints) {

                    final Set<Geocache> cachesToDisplay = overlay.search.getCachesFromSearchResult(LoadFlags.LOAD_WAYPOINTS);
                    previousViewport = viewportNow;
                    overlay.showWaypoints = showWaypointsNow;
                    overlay.display(cachesToDisplay);

                } else if (previousViewport != null && !previousViewport.equals(viewportNow)) {

                    overlay.updateTitle();
                }
            } catch (final Exception e) {
                Log.w("CachesOverlay.LoadTimer.run", e);
            } finally {
                overlay.updating = false;
            }
        }
    }

    private void display(final Set<Geocache> cachesToDisplay) {
        try {
            showProgress();
            update(cachesToDisplay, showWaypoints);
        } finally {
            hideProgress();
        }
    }

    @Override
    public void invalidate() {
        firstRun = true;

        super.invalidate();
    }

    @Override
    public void onDestroy() {
        timer.dispose();

        super.onDestroy();
    }
}
