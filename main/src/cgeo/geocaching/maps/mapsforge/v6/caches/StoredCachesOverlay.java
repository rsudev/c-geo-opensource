package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;

import java.util.Collections;
import java.util.Set;

import org.mapsforge.map.layer.Layer;

public class StoredCachesOverlay extends AbstractCachesOverlay {

    Set<Geocache> cachesFromSearchResult = Collections.emptySet();

    public StoredCachesOverlay(final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer anchorLayer, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, anchorLayer, mapHandlers);
    }


    @Override
    void load() {
        try {
            showProgress();

            final SearchResult searchResult = new SearchResult(DataStore.loadStoredInViewport(getViewport().resize(1.2), Settings.getCacheType()));

            cachesFromSearchResult = searchResult.getCachesFromSearchResult(LoadFlags.LOAD_WAYPOINTS);

        } finally {
            hideProgress();
        }
    }

    @Override
    void update() {
        try {
            showProgress();

            filter(cachesFromSearchResult);

            // render
            update(cachesFromSearchResult);

        } finally {
            hideProgress();
        }
    }
}
