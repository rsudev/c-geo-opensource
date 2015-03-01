package cgeo.geocaching.apps.cachelist;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.apps.AbstractApp;
import cgeo.geocaching.maps.mapsforge.v5.NewMap;

import android.app.Activity;

import java.util.List;

class InternalCacheListMapNew extends AbstractApp implements CacheListApp {

    InternalCacheListMapNew() {
        super(getString(R.string.cache_menu_new_map), null);
    }

    @Override
    public boolean isInstalled() {
        return true;
    }

    @Override
    public boolean invoke(final List<Geocache> caches, final Activity activity, final SearchResult search) {
        NewMap.startActivitySearch(activity, search, null);
        return true;
    }
}
