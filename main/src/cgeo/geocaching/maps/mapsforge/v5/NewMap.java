package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CachePopup;
import cgeo.geocaching.DataStore;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.Intents;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.activity.AbstractActionBarActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.connector.gc.GCMap;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.CGeoMap.MapMode;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import rx.Subscription;
import rx.subscriptions.Subscriptions;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NewMap extends AbstractActionBarActivity {

    private MfMapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;
    private PositionLayer positionLayer;
    private NavigationLayer navigationLayer;
    private CachesOverlay searchOverlay;
    private StoredCachesOverlay storedOverlay;
    private LiveCachesOverlay liveOverlay;
    private final List<SeparatorLayer> separators = new ArrayList<>();
    private final TapHandler tapHandler = new TapHandler(this);
    private TapHandlerLayer tapHandlerLayer;

    private DistanceView distanceView;

    private DragHandler dragHandler;

    private String mapTitle;
    private String geocodeIntent;
    private Geopoint coordsIntent;
    private SearchResult searchIntent;
    private MapState mapStateIntent = null;

    final private GeoDirHandler geoDirUpdate = new UpdateLoc(this);
    /**
     * initialization with an empty subscription to make static code analysis tools more happy
     */
    private Subscription resumeSubscription = Subscriptions.empty();
    private CheckBox myLocSwitch;
    private static boolean followMyLocation;

    final private static String BUNDLE_MAP_STATE = "mapState";

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidGraphicFactory.createInstance(this.getApplication());

        // Get parameters from the intent
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            geocodeIntent = extras.getString(Intents.EXTRA_GEOCODE);
            searchIntent = extras.getParcelable(Intents.EXTRA_SEARCH);
            coordsIntent = extras.getParcelable(Intents.EXTRA_COORDS);
            mapTitle = extras.getString(Intents.EXTRA_TITLE);
            mapStateIntent = extras.getParcelable(Intents.EXTRA_MAPSTATE);
        }
        if (StringUtils.isBlank(mapTitle)) {
            mapTitle = res.getString(R.string.map_map);
        }

        // Get fresh map information from the bundle if any
        if (savedInstanceState != null) {
            mapStateIntent = savedInstanceState.getParcelable(BUNDLE_MAP_STATE);
            //            isLiveEnabled = savedInstanceState.getBoolean(BUNDLE_LIVE_ENABLED, false);
            //            trailHistory = savedInstanceState.getParcelableArrayList(BUNDLE_TRAIL_HISTORY);
            followMyLocation = mapStateIntent.followsMyLocation();
        }

        ActivityMixin.onCreate(this, true);

        // set layout
        ActivityMixin.setTheme(this);

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

        // attach drag handler
        dragHandler = new DragHandler(this);
        mapView.setOnTouchListener(dragHandler);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.map_activity, menu);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            /* if we have an Actionbar find the my position toggle */
            final MenuItem item = menu.findItem(R.id.menu_toggle_mypos);
            myLocSwitch = new CheckBox(this);
            myLocSwitch.setButtonDrawable(R.drawable.ic_menu_myposition);
            item.setActionView(myLocSwitch);
            initMyLocationSwitchButton(myLocSwitch);
        } else {
            // Already on the fake Actionbar
            menu.removeItem(R.id.menu_toggle_mypos);
        }

        return result;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mapStateIntent != null) {
            this.mapView.getModel().mapViewPosition.setCenter(mapStateIntent.getCenter());
            this.mapView.getModel().mapViewPosition.setZoomLevel((byte) mapStateIntent.getZoomLevel());
        } else {
        final GeoPointImpl center = Settings.getMapCenter();

        final LatLong myCenter = new LatLong(center.getLatitudeE6() / 1.0e6, center.getLongitudeE6() / 1.0e6);

        this.mapView.getModel().mapViewPosition.setCenter(myCenter);
        this.mapView.getModel().mapViewPosition.setZoomLevel((byte) (Settings.getMapZoom(MapMode.SINGLE) - 3));
        }
        // tile renderer layer using internal render theme
        this.tileRendererLayer = new TileRendererLayer(tileCache, new MapFile(NewMap.getMapFile()),
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        // only once a layer is associated with a mapView the rendering starts
        this.mapView.getLayerManager().getLayers().add(this.tileRendererLayer);

        // NavigationLayer
        Geopoint navTarget = this.coordsIntent;
        if (navTarget == null && StringUtils.isNotEmpty(this.geocodeIntent)) {
            final Viewport bounds = DataStore.getBounds(this.geocodeIntent);
            if (bounds != null) {
                navTarget = bounds.center;
            }
        }
        this.navigationLayer = new NavigationLayer(navTarget);
        this.mapView.getLayerManager().getLayers().add(this.navigationLayer);

        // TapHandler
        this.tapHandlerLayer = new TapHandlerLayer(this.tapHandler);
        this.mapView.getLayerManager().getLayers().add(this.tapHandlerLayer);

        // Caches overlay
        if (this.searchIntent != null) {
            this.searchOverlay = new CachesOverlay(this.searchIntent, this.mapView, this.tapHandlerLayer, this.tapHandler);
        } else if (StringUtils.isNotEmpty(this.geocodeIntent)) {
            this.searchOverlay = new CachesOverlay(this.geocodeIntent, this.mapView, this.tapHandlerLayer, this.tapHandler);
        }

        // Live map
        if (/* live mode */true) {
            final SeparatorLayer separator1 = new SeparatorLayer();
            this.separators.add(separator1);
            this.mapView.getLayerManager().getLayers().add(separator1);
            this.storedOverlay = new StoredCachesOverlay(this.mapView, separator1, this.tapHandler);
            final SeparatorLayer separator2 = new SeparatorLayer();
            this.separators.add(separator2);
            this.mapView.getLayerManager().getLayers().add(separator2);
            this.liveOverlay = new LiveCachesOverlay(this.mapView, separator2, this.tapHandler);
        }

        // Position layer
        this.positionLayer = new PositionLayer();
        this.mapView.getLayerManager().getLayers().add(positionLayer);

        //Distance view
        this.distanceView = new DistanceView(navTarget, (TextView) findViewById(R.id.distance));

        this.resumeSubscription = Subscriptions.from(this.geoDirUpdate.start(GeoDirHandler.UPDATE_GEODIR));
    }

    @Override
    protected void onStop() {
        super.onStop();

        this.resumeSubscription.unsubscribe();

        this.mapView.getLayerManager().getLayers().remove(this.positionLayer);
        if (this.searchOverlay != null) {
            this.searchOverlay.onDestroy();
            this.searchOverlay = null;
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
        this.mapView.getLayerManager().getLayers().remove(this.navigationLayer);
        this.navigationLayer = null;
        this.mapView.getLayerManager().getLayers().remove(this.tileRendererLayer);
        this.tileRendererLayer.onDestroy();
        this.tileRendererLayer = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        this.tileCache.destroy();
        this.mapView.getModel().mapViewPosition.destroy();
        this.mapView.destroy();
        AndroidResourceBitmap.clearResourceBitmaps();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        final MapState state = new MapState(mapView.getModel().mapViewPosition.getCenter(),
                mapView.getModel().mapViewPosition.getZoomLevel(),
                followMyLocation,
                false);
        outState.putParcelable(BUNDLE_MAP_STATE, state);
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

    private void initMyLocationSwitchButton(final CheckBox locSwitch) {
        myLocSwitch = locSwitch;
        /*
         * TODO: Switch back to ImageSwitcher for animations?
         * myLocSwitch.setFactory(this);
         * myLocSwitch.setInAnimation(activity, android.R.anim.fade_in);
         * myLocSwitch.setOutAnimation(activity, android.R.anim.fade_out);
         */
        myLocSwitch.setOnClickListener(new MyLocationListener(this));
        switchMyLocationButton();
    }

    // switch My Location button image
    private void switchMyLocationButton() {
        // FIXME: temporary workaround for the absence of "follow my location" on Android 3.x (see issue #4289).
        if (myLocSwitch != null) {
            myLocSwitch.setChecked(followMyLocation);
            if (followMyLocation) {
                myLocationInMiddle(Sensors.getInstance().currentGeo());
            }
        }
    }

    // set my location listener
    private static class MyLocationListener implements View.OnClickListener {

        private final WeakReference<NewMap> mapRef;

        public MyLocationListener(@NonNull final NewMap map) {
            mapRef = new WeakReference<>(map);
        }

        @Override
        public void onClick(final View view) {
            final NewMap map = mapRef.get();
            if (map != null) {
                map.onFollowMyLocationClicked();
            }
        }
    }

    private void onFollowMyLocationClicked() {
        followMyLocation = !followMyLocation;
        switchMyLocationButton();
    }

    // Set center of map to my location if appropriate.
    private void myLocationInMiddle(final GeoData geo) {
        if (followMyLocation) {
            centerMap(geo.getCoords());
        }
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
            mapIntent.putExtra(Intents.EXTRA_TITLE, title);
        }
        fromActivity.startActivity(mapIntent);
    }

    public static void startActivityGeoCode(final Activity fromActivity, final String geocode) {
        final Intent mapIntent = new Intent(fromActivity, NewMap.class);
        mapIntent.putExtra(Intents.EXTRA_MAP_MODE, MapMode.SINGLE);
        mapIntent.putExtra(Intents.EXTRA_LIVE_ENABLED, false);
        mapIntent.putExtra(Intents.EXTRA_GEOCODE, geocode);
        mapIntent.putExtra(Intents.EXTRA_TITLE, geocode);
        fromActivity.startActivity(mapIntent);
    }

    public static void startActivitySearch(final Activity fromActivity, final SearchResult search, final String title) {
        final Intent mapIntent = new Intent(fromActivity, NewMap.class);
        mapIntent.putExtra(Intents.EXTRA_SEARCH, search);
        mapIntent.putExtra(Intents.EXTRA_MAP_MODE, MapMode.LIST);
        mapIntent.putExtra(Intents.EXTRA_LIVE_ENABLED, false);
        if (StringUtils.isNotBlank(title)) {
            mapIntent.putExtra(Intents.EXTRA_TITLE, title);
        }
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
                            if (NewMap.followMyLocation) {
                                map.centerMap(new Geopoint(currentLocation));
                            }
                        }

                        if (needsRepaintForDistanceOrAccuracy || needsRepaintForHeading) {

                            map.navigationLayer.setCoordinates(currentLocation);
                            map.distanceView.setCoordinates(currentLocation);
                            map.positionLayer.setCoordinates(currentLocation);
                            map.positionLayer.setHeading(currentHeading);
                            map.positionLayer.requestRedraw();
                        }
                    }
                } catch (final RuntimeException e) {
                    Log.w("Failed to update location", e);
                }
            }
        }

        boolean needsRepaintForHeading() {
            final NewMap map = mapRef.get();
            if (map == null) {
                return false;
            }
            return Math.abs(AngleUtils.difference(currentHeading, map.positionLayer.getHeading())) > MIN_HEADING_DELTA;
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

    private static class DragHandler implements OnTouchListener {

        private final WeakReference<NewMap> mapRef;

        public DragHandler(final NewMap parent) {
            mapRef = new WeakReference<>(parent);
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                final NewMap map = mapRef.get();
                if (map != null && NewMap.followMyLocation) {
                    NewMap.followMyLocation = false;
                    map.switchMyLocationButton();
                }
            }

            return false;
        }
    }

    public void showSelection(final ArrayList<String> geocodes) {
        try {

            if (geocodes.size() == 0) {
                return;
            }

            String geocode = "";

            if (geocodes.size() > 1) {

                final CharSequence[] items = geocodes.toArray(new String[] {});

                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Select a cache");
                builder.setItems(items, new SelectionClickListener(geocodes));
                builder.show();

            } else {
                geocode = geocodes.get(0);
            }

            showPopup(geocode);

        } catch (final NotFoundException e) {
            Log.e("NewMap.showPopup", e);
        }

        return;
    }

    private class SelectionClickListener implements DialogInterface.OnClickListener {

        private final ArrayList<String> items;

        public SelectionClickListener(final ArrayList<String> items) {
            this.items = items;
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            if (which >= 0 && which < items.size()) {
                final String geocode = items.get(which);
                showPopup(geocode);
            }
        }

    }

    private void showPopup(final String geocode) {
        try {

            if (StringUtils.isEmpty(geocode)) {
                return;
            }

            final Geocache cache = DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);

            if (cache != null) {
                final RequestDetailsThread requestDetailsThread = new RequestDetailsThread(cache, this);
                if (!requestDetailsThread.requestRequired()) {
                    // don't show popup if we have enough details
                    // progress.dismiss();
                }
                requestDetailsThread.start();
                return;
            }

        } catch (final NotFoundException e) {
            Log.e("NewMap.showPopup", e);
        }

        return;
    }

    private class RequestDetailsThread extends Thread {

        private final @NonNull Geocache cache;
        private final @NonNull WeakReference<NewMap> map;

        public RequestDetailsThread(final @NonNull Geocache cache, final @NonNull NewMap map) {
            this.cache = cache;
            this.map = new WeakReference<>(map);
        }

        public boolean requestRequired() {
            return CacheType.UNKNOWN == cache.getType() || cache.getDifficulty() == 0;
        }

        @Override
        public void run() {
            final NewMap map = this.map.get();
            if (map == null) {
                return;
            }
            if (requestRequired()) {
                /* final SearchResult search = */GCMap.searchByGeocodes(Collections.singleton(cache.getGeocode()));
            }
            CachePopup.startActivity(map, cache.getGeocode());
        }
    }

}
