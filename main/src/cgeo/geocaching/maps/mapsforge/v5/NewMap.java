package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.Intents;
import cgeo.geocaching.R;
import cgeo.geocaching.activity.AbstractActionBarActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.maps.CGeoMap.MapMode;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import rx.Subscription;
import rx.subscriptions.Subscriptions;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import java.io.File;
import java.lang.ref.WeakReference;

public class NewMap extends AbstractActionBarActivity {

    private MfMapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    private String mapTitle;
    private String geocodeIntent;
    private Geopoint coordsIntent;

    final private GeoDirHandler geoDirUpdate = new UpdateLoc(this);
    /**
     * initialization with an empty subscription to make static code analysis tools more happy
     */
    private Subscription resumeSubscription = Subscriptions.empty();

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidGraphicFactory.createInstance(this.getApplication());

        // Get parameters from the intent
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            geocodeIntent = extras.getString(Intents.EXTRA_GEOCODE);
            coordsIntent = extras.getParcelable(Intents.EXTRA_COORDS);
            mapTitle = extras.getString(Intents.EXTRA_MAP_TITLE);
        }
        if (StringUtils.isBlank(mapTitle)) {
            mapTitle = res.getString(R.string.map_map);
        }

        ActivityMixin.onCreate(this, true);

        // set layout
        ActivityMixin.setTheme(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.map_mapsforge_v5);
        setTitle(res.getString(R.string.map_map));

        // initialize map
        mapView = (MfMapView) findViewById(R.id.mfmapv5);

        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getMapZoomControls().setZoomLevelMin((byte) 10);
        mapView.getMapZoomControls().setZoomLevelMax((byte) 20);

        // create a tile cache of suitable size
        tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());
    }

    @Override
    protected void onStart() {
        super.onStart();

        final GeoPointImpl center = Settings.getMapCenter();

        final LatLong myCenter = new LatLong(center.getLatitudeE6() / 1.0e6, center.getLongitudeE6() / 1.0e6);

        this.mapView.getModel().mapViewPosition.setCenter(myCenter);
        this.mapView.getModel().mapViewPosition.setZoomLevel((byte) Settings.getMapZoom(MapMode.SINGLE));

        // tile renderer layer using internal render theme
        this.tileRendererLayer = new TileRendererLayer(tileCache,
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setMapFile(NewMap.getMapFile());
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        // only once a layer is associated with a mapView the rendering starts
        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);

        resumeSubscription = Subscriptions.from(geoDirUpdate.start(GeoDirHandler.UPDATE_GEODIR));
    }

    @Override
    protected void onStop() {
        super.onStop();

        resumeSubscription.unsubscribe();

        this.mapView.getLayerManager().getLayers().remove(this.tileRendererLayer);
        this.tileRendererLayer.onDestroy();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        this.tileCache.destroy();
        this.mapView.getModel().mapViewPosition.destroy();
        this.mapView.destroy();
        AndroidResourceBitmap.clearResourceBitmaps();
    }

    private void centerMap(final Geopoint geopoint) {
        this.mapView.getModel().mapViewPosition.setCenter(new LatLong(geopoint.getLatitude(), geopoint.getLongitude()));
    }

    public Location getCoordinates() {
        final LatLong center = mapView.getModel().mapViewPosition.getCenter();
        final Location loc = new Location("newmap");
        loc.setLatitude(center.latitude);
        loc.setLongitude(center.longitude);
        return loc;
    }

    private static File getMapFile() {
        return new File(Settings.getMapFile());
    }

    public static void startActivityCoords(final Activity fromActivity, final Geopoint coords, final WaypointType type, final String title) {
        final Intent mapIntent = new Intent(fromActivity, NewMap.class);
        mapIntent.putExtra(Intents.EXTRA_MAP_MODE, MapMode.COORDS);
        mapIntent.putExtra(Intents.EXTRA_LIVE_ENABLED, false);
        mapIntent.putExtra(Intents.EXTRA_COORDS, coords);
        if (type != null) {
            mapIntent.putExtra(Intents.EXTRA_WPTTYPE, type.id);
        }
        if (StringUtils.isNotBlank(title)) {
            mapIntent.putExtra(Intents.EXTRA_MAP_TITLE, title);
        }
        fromActivity.startActivity(mapIntent);
    }

    public static void startActivityGeoCode(final Activity fromActivity, final String geocode) {
        final Intent mapIntent = new Intent(fromActivity, NewMap.class);
        mapIntent.putExtra(Intents.EXTRA_MAP_MODE, MapMode.SINGLE);
        mapIntent.putExtra(Intents.EXTRA_LIVE_ENABLED, false);
        mapIntent.putExtra(Intents.EXTRA_GEOCODE, geocode);
        mapIntent.putExtra(Intents.EXTRA_MAP_TITLE, geocode);
        fromActivity.startActivity(mapIntent);
    }

    // class: update location
    private static class UpdateLoc extends GeoDirHandler {
        // use the following constants for fine tuning - find good compromise between smooth updates and as less updates as possible

        // minimum time in milliseconds between position overlay updates
        private static final long MIN_UPDATE_INTERVAL = 500;
        // minimum change of heading in grad for position overlay update
        private static final float MIN_HEADING_DELTA = 15f;
        // minimum change of location in fraction of map width/height (whatever is smaller) for position overlay update
        private static final float MIN_LOCATION_DELTA = 0.01f;

        Location currentLocation = Sensors.getInstance().currentGeo();
        float currentHeading;

        private long timeLastPositionOverlayCalculation = 0;
        /**
         * weak reference to the outer class
         */
        private final WeakReference<NewMap> mapRef;

        public UpdateLoc(final NewMap map) {
            mapRef = new WeakReference<>(map);
        }

        @Override
        public void updateGeoDir(final GeoData geo, final float dir) {
            currentLocation = geo;
            currentHeading = AngleUtils.getDirectionNow(dir);
            repaintPositionOverlay();
        }

        /**
         * Repaint position overlay but only with a max frequency and if position or heading changes sufficiently.
         */
        void repaintPositionOverlay() {
            final long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis > timeLastPositionOverlayCalculation + MIN_UPDATE_INTERVAL) {
                timeLastPositionOverlayCalculation = currentTimeMillis;

                try {
                    final NewMap map = mapRef.get();
                    if (map != null) {
                        final boolean needsRepaintForDistanceOrAccuracy = needsRepaintForDistanceOrAccuracy();
                        final boolean needsRepaintForHeading = needsRepaintForHeading();

                        if (needsRepaintForDistanceOrAccuracy) {
                            //                            if (CGeoMap.followMyLocation) {
                            map.centerMap(new Geopoint(currentLocation));
                            //                            }
                        }

                        //                        if (needsRepaintForDistanceOrAccuracy || needsRepaintForHeading) {
                        //
                        //                            map.overlayPositionAndScale.setCoordinates(currentLocation);
                        //                            map.overlayPositionAndScale.setHeading(currentHeading);
                        //                            map.mapView.repaintRequired(map.overlayPositionAndScale);
                        //                        }
                    }
                } catch (final RuntimeException e) {
                    Log.w("Failed to update location", e);
                }
            }
        }

        boolean needsRepaintForHeading() {
            //            final NewMap map = mapRef.get();
            //            if (map == null) {
            return false;
            //            }
            //            return Math.abs(AngleUtils.difference(currentHeading, map.overlayPositionAndScale.getHeading())) > MIN_HEADING_DELTA;
        }

        boolean needsRepaintForDistanceOrAccuracy() {
            final NewMap map = mapRef.get();
            if (map == null) {
                return false;
            }
            final Location lastLocation = map.getCoordinates();

            float dist = Float.MAX_VALUE;
            if (lastLocation != null) {
                if (lastLocation.getAccuracy() != currentLocation.getAccuracy()) {
                    return true;
                }
                dist = currentLocation.distanceTo(lastLocation);
            }

            final float[] mapDimension = new float[1];
            if (map.mapView.getWidth() < map.mapView.getHeight()) {
                final double span = map.mapView.getLongitudeSpan() / 1e6;
                Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), currentLocation.getLatitude(), currentLocation.getLongitude() + span, mapDimension);
            } else {
                final double span = map.mapView.getLatitudeSpan() / 1e6;
                Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), currentLocation.getLatitude() + span, currentLocation.getLongitude(), mapDimension);
            }

            return dist > (mapDimension[0] * MIN_LOCATION_DELTA);
        }
    }

}
