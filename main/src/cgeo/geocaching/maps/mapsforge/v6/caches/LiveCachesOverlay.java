package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.gc.GCLogin;
import cgeo.geocaching.connector.gc.MapTokens;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.RemoveFlag;
import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.Log;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.mapsforge.map.layer.Layer;

class LiveCachesOverlay extends AbstractCachesOverlay {

    private boolean downloading = false;
    private long loadThreadRun = -1;
    private MapTokens tokens;
    private Set<Geocache> result = Collections.emptySet();

    LiveCachesOverlay(final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer anchorLayer, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, anchorLayer, mapHandlers);
    }

    @Override
    void load() {
        if (isDownloading()) {
            return;
        }
        try {
            final long currentTime = System.currentTimeMillis();

            if (1000 < (currentTime - loadThreadRun)) {
                downloading = true;
                download();
            }
        } catch (final Exception e) {
            Log.w("LiveCachesOverlay.startLoadtimer.start", e);
        } finally {
            downloading = false;
        }
    }

    @Override
    void update() {
        try {
            showProgress();

            filter(result);
            //render
            update(result);
        } finally {
            hideProgress();
        }
    }

    private void download() {
        try {
            showProgress();

            if (Settings.isGCConnectorActive() && tokens == null) {
                tokens = GCLogin.getInstance().getMapTokens();
                if (StringUtils.isEmpty(tokens.getUserSession()) || StringUtils.isEmpty(tokens.getSessionToken())) {
                    tokens = null;
                    //TODO: show missing map token toast
                    //                    if (!noMapTokenShowed) {
                    //                        ActivityMixin.showToast(activity, res.getString(R.string.map_token_err));
                    //                        noMapTokenShowed = true;
                    //                    }
                }
            }
            final SearchResult searchResult = ConnectorFactory.searchByViewport(getViewport().resize(1.2), tokens);

            // update the caches
            // first remove filtered out
            final Set<String> filteredCodes = searchResult.getFilteredGeocodes();
            Log.d("Filtering out " + filteredCodes.size() + " caches: " + filteredCodes.toString());
            DataStore.removeCaches(filteredCodes, EnumSet.of(RemoveFlag.CACHE));

            result = DataStore.loadCachedInViewport(getViewport().resize(1.2), Settings.getCacheType()).getCachesFromSearchResult(LoadFlags.LOAD_CACHE_ONLY);

            Log.d(String.format(Locale.ENGLISH, "Live caches found: %d", result.size()));
        } finally {
            loadThreadRun = System.currentTimeMillis();
            hideProgress();
        }
    }

    boolean isDownloading() {
        return downloading;
    }
}
