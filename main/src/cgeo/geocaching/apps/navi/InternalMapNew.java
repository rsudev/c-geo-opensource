package cgeo.geocaching.apps.navi;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.maps.mapsforge.v5.NewMap;

import android.app.Activity;

class InternalMapNew extends AbstractPointNavigationApp {

    InternalMapNew() {
        super(getString(R.string.cache_menu_new_map), null);
    }

    @Override
    public boolean isInstalled() {
        return true;
    }

    @Override
    public void navigate(final Activity activity, final Geopoint coords) {
        NewMap.startActivityCoords(activity, coords, WaypointType.WAYPOINT, null);
    }

    @Override
    public void navigate(final Activity activity, final Waypoint waypoint) {
        NewMap.startActivityCoords(activity, waypoint.getCoords(), waypoint.getWaypointType(), waypoint.getName());
    }

    @Override
    public void navigate(final Activity activity, final Geocache cache) {
        NewMap.startActivityGeoCode(activity, cache.getGeocode());
    }

}
