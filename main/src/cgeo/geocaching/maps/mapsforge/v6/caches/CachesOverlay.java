package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.models.Geocache;

import java.util.Set;

import org.mapsforge.map.layer.Layer;

public class CachesOverlay extends AbstractCachesOverlay {

    private final SearchResult search;
    private boolean firstRun = true;

    CachesOverlay(final SearchResult search, final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer anchorLayer, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, anchorLayer, mapHandlers);

        this.search = search;
    }

    CachesOverlay(final String geocode, final int overlayId, final Set<GeoEntry> geoEntries, final CachesBundle bundle, final Layer layerAnchor, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, bundle, layerAnchor, mapHandlers);

        this.search = new SearchResult();
        this.search.addGeocode(geocode);
    }

    @Override
    void load() {
        // Nothing to do for static overlay
    }

    @Override
    void update() {
        if (!firstRun) {
            return;
        }

        final Set<Geocache> cachesToDisplay = search.getCachesFromSearchResult(LoadFlags.LOAD_WAYPOINTS);
        firstRun = false;
        try {
            showProgress();
            update(cachesToDisplay);
        } finally {
            hideProgress();
        }
    }
}
