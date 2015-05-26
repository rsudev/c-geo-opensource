package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.utils.RxUtils;

import org.mapsforge.map.layer.Layer;

import rx.functions.Action0;

import android.os.Handler;

public class SinglePointOverlay extends AbstractCachesOverlay {

    private final Geopoint coords;
    private final WaypointType type;

    public SinglePointOverlay(final Geopoint coords, final WaypointType type, final MfMapView mapView, final Layer layerAnchor, final TapHandler tapHandler, final Handler displayHandler) {
        super(mapView, layerAnchor, tapHandler, displayHandler);

        this.coords = coords;
        this.type = type;

        RxUtils.computationScheduler.createWorker().schedule(new Action0() {

            @Override
            public void call() {
                fill();
            }

        });
    }

    private void fill() {
        try {
            //            showProgressHandler.sendEmptyMessage(SHOW_PROGRESS);
            clearLayers();

            // construct waypoint
            final Waypoint waypoint = new Waypoint("", type, false);
            waypoint.setCoords(coords);

            addItem(waypoint);

            addLayers();

            repaint();
        } finally {
            //            showProgressHandler.sendEmptyMessage(HIDE_PROGRESS);
        }
    }
}
