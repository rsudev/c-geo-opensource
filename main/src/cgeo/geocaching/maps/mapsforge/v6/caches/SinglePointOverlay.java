package cgeo.geocaching.maps.mapsforge.v6.caches;

import cgeo.geocaching.maps.mapsforge.v6.MapHandlers;
import cgeo.geocaching.maps.mapsforge.v6.MfMapView;
import cgeo.geocaching.maps.mapsforge.v6.NewMap;
import cgeo.geocaching.maps.mapsforge.v6.TapHandler;
import cgeo.geocaching.models.Waypoint;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.utils.AndroidRxUtils;

import org.mapsforge.map.layer.Layer;

import rx.functions.Action0;

import android.os.Handler;

import java.util.Set;

public class SinglePointOverlay extends AbstractCachesOverlay {

    private final Geopoint coords;
    private final WaypointType type;

    public SinglePointOverlay(final Geopoint coords, final WaypointType type, final int overlayId, final Set<GeoEntry> geoEntries, final MfMapView mapView, final Layer anchorLayer, final MapHandlers mapHandlers) {
        super(overlayId, geoEntries, mapView, anchorLayer, mapHandlers);

        this.coords = coords;
        this.type = type;

        AndroidRxUtils.computationScheduler.createWorker().schedule(new Action0() {

            @Override
            public void call() {
                fill();
            }

        });
    }

    private void fill() {
        try {
            showProgress();

            clearLayers();

            // construct waypoint
            final Waypoint waypoint = new Waypoint("", type, false);
            waypoint.setCoords(coords);

            addItem(waypoint);

            addLayers();

            repaint();
        } finally {
            hideProgress();
        }
    }
}
