package cgeo.geocaching.maps.mapsforge.v6;

import cgeo.geocaching.AbstractDialogFragment;
import cgeo.geocaching.AbstractDialogFragment.TargetInfo;
import cgeo.geocaching.CacheListActivity;
import cgeo.geocaching.CachePopup;
import cgeo.geocaching.CompassActivity;
import cgeo.geocaching.EditWaypointActivity;
import cgeo.geocaching.Intents;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.WaypointPopup;
import cgeo.geocaching.activity.AbstractActionBarActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.connector.gc.GCMap;
import cgeo.geocaching.connector.gc.Tile;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.CoordinatesType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.LivemapStrategy;
import cgeo.geocaching.maps.MapMode;
import cgeo.geocaching.maps.MapOptions;
import cgeo.geocaching.maps.MapProviderFactory;
import cgeo.geocaching.maps.MapState;
import cgeo.geocaching.maps.interfaces.MapSource;
import cgeo.geocaching.maps.interfaces.OnMapDragListener;
import cgeo.geocaching.maps.mapsforge.MapsforgeMapSource;
import cgeo.geocaching.maps.mapsforge.v6.caches.CachesBundle;
import cgeo.geocaching.maps.mapsforge.v6.caches.GeoitemRef;
import cgeo.geocaching.maps.mapsforge.v6.layers.HistoryLayer;
import cgeo.geocaching.maps.mapsforge.v6.layers.ITileLayer;
import cgeo.geocaching.maps.mapsforge.v6.layers.NavigationLayer;
import cgeo.geocaching.maps.mapsforge.v6.layers.PositionLayer;
import cgeo.geocaching.maps.mapsforge.v6.layers.TapHandlerLayer;
import cgeo.geocaching.maps.routing.Routing;
import cgeo.geocaching.maps.routing.RoutingMode;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.permission.PermissionGrantedCallback;
import cgeo.geocaching.permission.PermissionHandler;
import cgeo.geocaching.permission.PermissionRequestContext;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.DisposableHandler;
import cgeo.geocaching.utils.Formatter;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.functions.Action1;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import butterknife.ButterKnife;
import io.reactivex.disposables.CompositeDisposable;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.util.Parameters;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.input.MapZoomControls;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.rule.RenderThemeHandler;
import org.xmlpull.v1.XmlPullParserException;

@SuppressLint("ClickableViewAccessibility")
public class NewMap extends AbstractActionBarActivity implements XmlRenderThemeMenuCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private MfMapView mapView;
    private TileCache tileCache;
    private ITileLayer tileLayer;
    private HistoryLayer historyLayer;
    private PositionLayer positionLayer;
    private NavigationLayer navigationLayer;
    private CachesBundle caches;
    private final MapHandlers mapHandlers = new MapHandlers(new TapHandler(this), new DisplayHandler(this), new ShowProgressHandler(this));

    private XmlRenderThemeStyleMenu styleMenu;
    private SharedPreferences sharedPreferences;

    private DistanceView distanceView;

    private ArrayList<Location> trailHistory = null;

    private String targetGeocode = null;
    private Geopoint lastNavTarget = null;
    private final Queue<String> popupGeocodes = new ConcurrentLinkedQueue<>();

    private ProgressDialog waitDialog;
    private LoadDetails loadDetailsThread;

    private String themeSettingsPref = "";

    private final UpdateLoc geoDirUpdate = new UpdateLoc(this);
    /**
     * initialization with an empty subscription to make static code analysis tools more happy
     */
    private final CompositeDisposable resumeDisposables = new CompositeDisposable();
    private CheckBox myLocSwitch;
    private MapOptions mapOptions;
    private TargetView targetView;

    private static boolean followMyLocation = true;

    private static final String BUNDLE_MAP_STATE = "mapState";
    private static final String BUNDLE_TRAIL_HISTORY = "trailHistory";

    // Handler messages
    // DisplayHandler
    public static final int UPDATE_TITLE = 0;
    public static final int INVALIDATE_MAP = 1;
    // ShowProgressHandler
    public static final int HIDE_PROGRESS = 0;
    public static final int SHOW_PROGRESS = 1;
    // LoadDetailsHandler
    public static final int UPDATE_PROGRESS = 0;
    public static final int FINISHED_LOADING_DETAILS = 1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("NewMap: onCreate");

        ResourceBitmapCacheMonitor.addRef();
        AndroidGraphicFactory.createInstance(this.getApplication());

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // some tiles are rather big, see https://github.com/mapsforge/mapsforge/issues/868
        Parameters.MAXIMUM_BUFFER_SIZE = 6500000;

        // Get parameters from the intent
        mapOptions = new MapOptions(this, getIntent().getExtras());

        // Get fresh map information from the bundle if any
        if (savedInstanceState != null) {
            mapOptions.mapState = savedInstanceState.getParcelable(BUNDLE_MAP_STATE);
            trailHistory = savedInstanceState.getParcelableArrayList(BUNDLE_TRAIL_HISTORY);
            followMyLocation = mapOptions.mapState.followsMyLocation();
        } else {
            followMyLocation = followMyLocation && mapOptions.mapMode == MapMode.LIVE;
        }

        ActivityMixin.onCreate(this, true);

        // set layout
        ActivityMixin.setTheme(this);

        setContentView(R.layout.map_mapsforge_v6);
        setTitle();

        // initialize map
        mapView = (MfMapView) findViewById(R.id.mfmapv5);

        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);

        // create a tile cache of suitable size. always initialize it based on the smallest tile size to expect (256 for online tiles)
        tileCache = AndroidUtil.createTileCache(this, "mapcache", 256, 1f, this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        // attach drag handler
        final DragHandler dragHandler = new DragHandler(this);
        mapView.setOnMapDragListener(dragHandler);

        // prepare initial settings of mapView
        if (mapOptions.mapState != null) {
            this.mapView.getModel().mapViewPosition.setCenter(MapsforgeUtils.toLatLong(mapOptions.mapState.getCenter()));
            this.mapView.setMapZoomLevel((byte) mapOptions.mapState.getZoomLevel());
            this.targetGeocode = mapOptions.mapState.getTargetGeocode();
            this.lastNavTarget = mapOptions.mapState.getLastNavTarget();
            mapOptions.isLiveEnabled = mapOptions.mapState.isLiveEnabled();
            mapOptions.isStoredEnabled = mapOptions.mapState.isStoredEnabled();
        } else if (mapOptions.searchResult != null) {
            final Viewport viewport = DataStore.getBounds(mapOptions.searchResult.getGeocodes());

            if (viewport != null) {
                postZoomToViewport(viewport);
            }
        } else if (StringUtils.isNotEmpty(mapOptions.geocode)) {
            final Viewport viewport = DataStore.getBounds(mapOptions.geocode);

            if (viewport != null) {
                postZoomToViewport(viewport);
            }
            targetGeocode = mapOptions.geocode;
        } else if (mapOptions.coords != null) {
            postZoomToViewport(new Viewport(mapOptions.coords, 0, 0));
        } else {
            postZoomToViewport(new Viewport(Settings.getMapCenter().getCoords(), 0, 0));
        }
        prepareFilterBar();
        Routing.connect();
    }

    private void postZoomToViewport(final Viewport viewport) {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                mapView.zoomToViewport(viewport);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final boolean result = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.map_activity, menu);

        MapProviderFactory.addMapviewMenuItems(menu);

        final MenuItem item = menu.findItem(R.id.menu_toggle_mypos);
        myLocSwitch = new CheckBox(this);
        myLocSwitch.setButtonDrawable(R.drawable.ic_menu_myposition);
        item.setActionView(myLocSwitch);
        initMyLocationSwitchButton(myLocSwitch);

        return result;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        for (final MapSource mapSource : MapProviderFactory.getMapSources()) {
            final MenuItem menuItem = menu.findItem(mapSource.getNumericalId());
            if (menuItem != null) {
                menuItem.setVisible(mapSource.isAvailable());
            }
        }

        try {
            final MenuItem itemMapLive = menu.findItem(R.id.menu_map_live);
            if (mapOptions.isLiveEnabled) {
                itemMapLive.setTitle(res.getString(R.string.map_live_disable));
            } else {
                itemMapLive.setTitle(res.getString(R.string.map_live_enable));
            }
            itemMapLive.setVisible(mapOptions.coords == null);

            final Set<String> visibleCacheGeocodes = caches.getVisibleCacheGeocodes();

            menu.findItem(R.id.menu_store_caches).setVisible(false);
            menu.findItem(R.id.menu_store_caches).setVisible(!caches.isDownloading() && !visibleCacheGeocodes.isEmpty());

            menu.findItem(R.id.menu_store_unsaved_caches).setVisible(false);
            menu.findItem(R.id.menu_store_unsaved_caches).setVisible(!caches.isDownloading() && new SearchResult(visibleCacheGeocodes).hasUnsavedCaches());

            menu.findItem(R.id.menu_mycaches_mode).setChecked(Settings.isExcludeMyCaches());
            menu.findItem(R.id.menu_disabled_mode).setChecked(Settings.isExcludeDisabledCaches());
            menu.findItem(R.id.menu_direction_line).setChecked(Settings.isMapDirection());
            //TODO: circles            menu.findItem(R.id.menu_circle_mode).setChecked(this.searchOverlay.getCircles());
            menu.findItem(R.id.menu_circle_mode).setVisible(false);
            menu.findItem(R.id.menu_trail_mode).setChecked(Settings.isMapTrail());

            menu.findItem(R.id.menu_theme_mode).setVisible(tileLayerHasThemes());
            menu.findItem(R.id.menu_theme_options).setVisible(styleMenu != null);

            menu.findItem(R.id.menu_as_list).setVisible(!caches.isDownloading() && caches.getVisibleCachesCount() > 1);

            menu.findItem(R.id.submenu_strategy).setVisible(mapOptions.isLiveEnabled);

            switch (Settings.getLiveMapStrategy()) {
                case FAST:
                    menu.findItem(R.id.menu_strategy_fast).setChecked(true);
                    break;
                case AUTO:
                    menu.findItem(R.id.menu_strategy_auto).setChecked(true);
                    break;
                default: // DETAILED
                    menu.findItem(R.id.menu_strategy_detailed).setChecked(true);
                    break;
            }

            menu.findItem(R.id.submenu_routing).setVisible(Routing.isAvailable());
            switch (Settings.getRoutingMode()) {
                case STRAIGHT:
                    menu.findItem(R.id.menu_routing_straight).setChecked(true);
                    break;
                case WALK:
                    menu.findItem(R.id.menu_routing_walk).setChecked(true);
                    break;
                case BIKE:
                    menu.findItem(R.id.menu_routing_bike).setChecked(true);
                    break;
                case CAR:
                    menu.findItem(R.id.menu_routing_car).setChecked(true);
                    break;
            }
            menu.findItem(R.id.menu_hint).setVisible(mapOptions.mapMode == MapMode.SINGLE);
            menu.findItem(R.id.menu_compass).setVisible(mapOptions.mapMode == MapMode.SINGLE);

        } catch (final RuntimeException e) {
            Log.e("NewMap.onPrepareOptionsMenu", e);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                ActivityMixin.navigateUp(this);
                return true;
            case R.id.menu_trail_mode:
                Settings.setMapTrail(!Settings.isMapTrail());
                historyLayer.requestRedraw();
                ActivityMixin.invalidateOptionsMenu(this);
                return true;
            case R.id.menu_direction_line:
                Settings.setMapDirection(!Settings.isMapDirection());
                navigationLayer.requestRedraw();
                ActivityMixin.invalidateOptionsMenu(this);
                return true;
            case R.id.menu_map_live:
                mapOptions.isLiveEnabled = !mapOptions.isLiveEnabled;
                if (mapOptions.isLiveEnabled) {
                    mapOptions.isStoredEnabled = true;
                }

                if (mapOptions.mapMode == MapMode.LIVE) {
                    Settings.setLiveMap(mapOptions.isLiveEnabled);
                }
                caches.enableStoredLayers(mapOptions.isStoredEnabled);
                caches.handleLiveLayers(mapOptions.isLiveEnabled);
                ActivityMixin.invalidateOptionsMenu(this);
                if (mapOptions.mapMode != MapMode.SINGLE) {
                    mapOptions.title = StringUtils.EMPTY;
                } else {
                    // reset target cache on single mode map
                    targetGeocode = mapOptions.geocode;
                }
                return true;
            case R.id.menu_store_caches:
                return storeCaches(caches.getVisibleCacheGeocodes());
            case R.id.menu_store_unsaved_caches:
                return storeCaches(getUnsavedGeocodes(caches.getVisibleCacheGeocodes()));
            case R.id.menu_circle_mode:
                //                overlayCaches.switchCircles();
                //                mapView.repaintRequired(overlayCaches);
                //                ActivityMixin.invalidateOptionsMenu(activity);
                return true;
            case R.id.menu_mycaches_mode:
                Settings.setExcludeMine(!Settings.isExcludeMyCaches());
                caches.invalidate();
                ActivityMixin.invalidateOptionsMenu(this);
                if (!Settings.isExcludeMyCaches()) {
                    Tile.cache.clear();
                }
                return true;
            case R.id.menu_disabled_mode:
                Settings.setExcludeDisabled(!Settings.isExcludeDisabledCaches());
                caches.invalidate();
                ActivityMixin.invalidateOptionsMenu(this);
                if (!Settings.isExcludeDisabledCaches()) {
                    Tile.cache.clear();
                }
                return true;
            case R.id.menu_theme_mode:
                selectMapTheme();
                return true;
            case R.id.menu_theme_options:
                final Intent intent = new Intent(this, RenderThemeSettings.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                if (styleMenu != null) {
                    intent.putExtra(RenderThemeSettings.RENDERTHEME_MENU, styleMenu);
                }
                startActivity(intent);
                return true;
            case R.id.menu_as_list:
                CacheListActivity.startActivityMap(this, new SearchResult(caches.getVisibleCacheGeocodes()));
                return true;
            case R.id.menu_strategy_fast:
                item.setChecked(true);
                Settings.setLiveMapStrategy(LivemapStrategy.FAST);
                return true;
            case R.id.menu_strategy_auto:
                item.setChecked(true);
                Settings.setLiveMapStrategy(LivemapStrategy.AUTO);
                return true;
            case R.id.menu_strategy_detailed:
                item.setChecked(true);
                Settings.setLiveMapStrategy(LivemapStrategy.DETAILED);
                return true;
            case R.id.menu_routing_straight:
                item.setChecked(true);
                Settings.setRoutingMode(RoutingMode.STRAIGHT);
                navigationLayer.requestRedraw();
                return true;
            case R.id.menu_routing_walk:
                item.setChecked(true);
                Settings.setRoutingMode(RoutingMode.WALK);
                navigationLayer.requestRedraw();
                return true;
            case R.id.menu_routing_bike:
                item.setChecked(true);
                Settings.setRoutingMode(RoutingMode.BIKE);
                navigationLayer.requestRedraw();
                return true;
            case R.id.menu_routing_car:
                item.setChecked(true);
                Settings.setRoutingMode(RoutingMode.CAR);
                navigationLayer.requestRedraw();
                return true;
            case R.id.menu_hint:
                menuShowHint();
                return true;
            case R.id.menu_compass:
                menuCompass();
                return true;
            default:
                final MapSource mapSource = MapProviderFactory.getMapSource(id);
                if (mapSource != null) {
                    item.setChecked(true);
                    changeMapSource(mapSource);
                    return true;
                }
        }
        return false;
    }

    private Set<String> getUnsavedGeocodes(final Set<String> geocodes) {
        final Set<String> unsavedGeocodes = new HashSet<>();

        for (final String geocode : geocodes) {
            if (!DataStore.isOffline(geocode, null)) {
                unsavedGeocodes.add(geocode);
            }
        }
        return unsavedGeocodes;
    }

    private boolean storeCaches(final Set<String> geocodes) {
        if (!caches.isDownloading()) {
            if (geocodes.isEmpty()) {
                ActivityMixin.showToast(this, res.getString(R.string.warn_save_nothing));
                return true;
            }

            if (Settings.getChooseList()) {
                // let user select list to store cache in
                new StoredList.UserInterface(this).promptForMultiListSelection(R.string.list_title, new Action1<Set<Integer>>() {
                    @Override
                    public void call(final Set<Integer> selectedListIds) {
                        storeCaches(geocodes, selectedListIds);
                    }
                }, true, Collections.singleton(StoredList.TEMPORARY_LIST.id), false);
            } else {
                storeCaches(geocodes, Collections.singleton(StoredList.STANDARD_LIST_ID));
            }
        }
        return true;
    }

    private void menuCompass() {
        final Geocache cache = getCurrentTargetCache();
        if (cache != null) {
            CompassActivity.startActivityCache(this, cache);
        }
    }

    private void menuShowHint() {
        final Geocache cache = getCurrentTargetCache();
        if (cache != null) {
            cache.showHintToast(this);
        }
    }

    private void prepareFilterBar() {
        // show the filter warning bar if the filter is set
        if (Settings.getCacheType() != CacheType.ALL) {
            final String cacheType = Settings.getCacheType().getL10n();
            final TextView filterTitleView = ButterKnife.findById(this, R.id.filter_text);
            filterTitleView.setText(cacheType);
            findViewById(R.id.filter_bar).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.filter_bar).setVisibility(View.GONE);
        }
    }

    /**
     * @param view Not used here, required by layout
     */
    public void showFilterMenu(final View view) {
        // do nothing, the filter bar only shows the global filter
    }

    private void selectMapTheme() {

        final File[] themeFiles = Settings.getMapThemeFiles();

        String currentTheme = StringUtils.EMPTY;
        final String currentThemePath = Settings.getCustomRenderThemeFilePath();
        if (StringUtils.isNotEmpty(currentThemePath)) {
            final File currentThemeFile = new File(currentThemePath);
            currentTheme = currentThemeFile.getName();
        }

        final List<String> names = new ArrayList<>();
        names.add(res.getString(R.string.map_theme_builtin));
        int currentItem = 0;
        for (final File file : themeFiles) {
            if (currentTheme.equalsIgnoreCase(file.getName())) {
                currentItem = names.size();
            }
            names.add(file.getName());
        }

        final int selectedItem = currentItem;

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.map_theme_select);

        builder.setSingleChoiceItems(names.toArray(new String[names.size()]), selectedItem, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int newItem) {
                if (newItem != selectedItem) {
                    // Adjust index because of <default> selection
                    if (newItem > 0) {
                        Settings.setCustomRenderThemeFile(themeFiles[newItem - 1].getPath());
                    } else {
                        Settings.setCustomRenderThemeFile(StringUtils.EMPTY);
                    }
                    setMapTheme();
                }
                dialog.cancel();
            }
        });

        builder.show();
    }

    protected void setMapTheme() {

        if (tileLayer == null || tileLayer.getTileLayer() == null) {
            return;
        }

        if (!tileLayer.hasThemes()) {
            tileLayer.getTileLayer().requestRedraw();
            return;
        }

        final TileRendererLayer rendererLayer = (TileRendererLayer) tileLayer.getTileLayer();

        final String themePath = Settings.getCustomRenderThemeFilePath();

        if (StringUtils.isEmpty(themePath)) {
            rendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
        } else {
            try {
                final XmlRenderTheme xmlRenderTheme = new ExternalRenderTheme(new File(themePath), this);
                // Validate the theme file
                RenderThemeHandler.getRenderTheme(AndroidGraphicFactory.INSTANCE, new DisplayModel(), xmlRenderTheme);
                rendererLayer.setXmlRenderTheme(xmlRenderTheme);
            } catch (final IOException e) {
                Log.w("Failed to set render theme", e);
                ActivityMixin.showApplicationToast(getString(R.string.err_rendertheme_file_unreadable));
                rendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
            } catch (final XmlPullParserException e) {
                Log.w("render theme invalid", e);
                ActivityMixin.showApplicationToast(getString(R.string.err_rendertheme_invalid));
                rendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
            }
        }
        tileCache.purge();
        rendererLayer.requestRedraw();
    }

    private void changeMapSource(@NonNull final MapSource newSource) {
        final MapSource oldSource = Settings.getMapSource();
        final boolean restartRequired = !MapProviderFactory.isSameActivity(oldSource, newSource);

        // Update MapSource in settings
        Settings.setMapSource(newSource);

        if (restartRequired) {
            mapRestart();
        } else if (mapView != null) {  // changeMapSource can be called by onCreate()
            switchTileLayer(newSource);
        }
    }

    /**
     * Restart the current activity with the default map source.
     */
    private void mapRestart() {
        mapOptions.mapState = currentMapState();
        finish();
        mapOptions.startIntent(this, Settings.getMapProvider().getMapClass());
    }

    /**
     * Get the current map state from the map view if it exists or from the mapStateIntent field otherwise.
     *
     * @return the current map state as an array of int, or null if no map state is available
     */
    private MapState currentMapState() {
        if (mapView == null) {
            return null;
        }
        final Geopoint mapCenter = mapView.getViewport().getCenter();
        return new MapState(mapCenter.getCoords(), mapView.getMapZoomLevel(), followMyLocation, false, targetGeocode, lastNavTarget, mapOptions.isLiveEnabled, mapOptions.isStoredEnabled);
    }


    private void switchTileLayer(final MapSource newSource) {
        final ITileLayer oldLayer = this.tileLayer;
        ITileLayer newLayer = null;
        if (newSource instanceof MapsforgeMapSource) {
            newLayer = ((MapsforgeMapSource) newSource).createTileLayer(tileCache, this.mapView.getModel().mapViewPosition);
        }

        // Exchange layer
        if (newLayer != null) {
            this.mapView.getModel().displayModel.setFixedTileSize(newLayer.getFixedTileSize());
            final MapZoomControls zoomControls = mapView.getMapZoomControls();
            zoomControls.setZoomLevelMax(newLayer.getZoomLevelMax());
            zoomControls.setZoomLevelMin(newLayer.getZoomLevelMin());

            final Layers layers = this.mapView.getLayerManager().getLayers();
            int index = 0;
            if (oldLayer != null) {
                index = layers.indexOf(oldLayer.getTileLayer()) + 1;
            }
            layers.add(index, newLayer.getTileLayer());
            this.tileLayer = newLayer;
            this.setMapTheme();
        } else {
            this.tileLayer = null;
        }

        // Cleanup
        if (oldLayer != null) {
            this.mapView.getLayerManager().getLayers().remove(oldLayer.getTileLayer());
            oldLayer.getTileLayer().onDestroy();
        }
        tileCache.purge();
    }

    private void resumeTileLayer() {
        if (this.tileLayer != null) {
            this.tileLayer.onResume();
        }
    }

    private void pauseTileLayer() {
        if (this.tileLayer != null) {
            this.tileLayer.onPause();
        }
    }

    private boolean tileLayerHasThemes() {
        if (tileLayer != null) {
            return tileLayer.hasThemes();
        }

        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("NewMap: onResume");

        resumeTileLayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("NewMap: onStart");

        initializeLayers();
    }

    private void initializeLayers() {

        switchTileLayer(Settings.getMapSource());

        // History Layer
        this.historyLayer = new HistoryLayer(trailHistory);
        this.mapView.getLayerManager().getLayers().add(this.historyLayer);

        // NavigationLayer
        Geopoint navTarget = lastNavTarget;
        if (navTarget == null) {
            navTarget = mapOptions.coords;
            if (navTarget == null && StringUtils.isNotEmpty(mapOptions.geocode)) {
                final Viewport bounds = DataStore.getBounds(mapOptions.geocode);
                if (bounds != null) {
                    navTarget = bounds.center;
                }
            }
        }
        this.navigationLayer = new NavigationLayer(navTarget);
        this.mapView.getLayerManager().getLayers().add(this.navigationLayer);

        // TapHandler
        final TapHandlerLayer tapHandlerLayer = new TapHandlerLayer(this.mapHandlers.getTapHandler());
        this.mapView.getLayerManager().getLayers().add(tapHandlerLayer);

        // Caches bundle
        if (mapOptions.searchResult != null) {
            this.caches = new CachesBundle(mapOptions.searchResult, this.mapView, this.mapHandlers);
        } else if (StringUtils.isNotEmpty(mapOptions.geocode)) {
            this.caches = new CachesBundle(mapOptions.geocode, this.mapView, this.mapHandlers);
        } else if (mapOptions.coords != null) {
            this.caches = new CachesBundle(mapOptions.coords, mapOptions.waypointType, this.mapView, this.mapHandlers);
        } else {
            caches = new CachesBundle(this.mapView, this.mapHandlers);
        }

        // Stored enabled map
        caches.enableStoredLayers(mapOptions.isStoredEnabled);
        // Live enabled map
        caches.handleLiveLayers(mapOptions.isLiveEnabled);

        // Position layer
        this.positionLayer = new PositionLayer();
        this.mapView.getLayerManager().getLayers().add(positionLayer);

        //Distance view
        this.distanceView = new DistanceView(navTarget, (TextView) findViewById(R.id.distance));

        //Target view
        this.targetView = new TargetView((TextView) findViewById(R.id.target), StringUtils.EMPTY, StringUtils.EMPTY);
        final Geocache target = getCurrentTargetCache();
        if (target != null) {
            targetView.setTarget(target.getGeocode(), target.getName());
        }

        // resume location access
        PermissionHandler.executeIfLocationPermissionGranted(this, new PermissionGrantedCallback(PermissionRequestContext.CacheDetailActivity) {

            @Override
            public void execute() {
                Log.d("NewMap.initializeLayersPermissionGrantedCallback.execute");
                final Sensors sensors = Sensors.getInstance();
                sensors.setupGeoDataObservables(Settings.useGooglePlayServices(), Settings.useLowPowerMode());
                sensors.setupDirectionObservable();

                resumeDisposables.add(geoDirUpdate.start(GeoDirHandler.UPDATE_GEODIR));
            }
        });
    }

    @Override
    public void onPause() {
        Log.d("NewMap: onPause");

        savePrefs();

        pauseTileLayer();

        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d("NewMap: onStop");

        waitDialog = null;
        terminateLayers();

        super.onStop();
    }

    private void terminateLayers() {
        this.resumeDisposables.clear();

        this.caches.onDestroy();
        this.caches = null;

        this.mapView.getLayerManager().getLayers().remove(this.positionLayer);
        this.positionLayer = null;
        this.mapView.getLayerManager().getLayers().remove(this.navigationLayer);
        this.navigationLayer = null;
        this.mapView.getLayerManager().getLayers().remove(this.historyLayer);
        this.historyLayer = null;

        if (this.tileLayer != null) {
            this.mapView.getLayerManager().getLayers().remove(this.tileLayer.getTileLayer());
            this.tileLayer.getTileLayer().onDestroy();
            this.tileLayer = null;
        }
    }

    /**
     * store caches, invoked by "store offline" menu item
     *
     * @param listIds the lists to store the caches in
     */
    private void storeCaches(final Set<String> geocodes, final Set<Integer> listIds) {

        final int count = geocodes.size();
        final LoadDetailsHandler loadDetailsHandler = new LoadDetailsHandler(count, this);

        waitDialog = new ProgressDialog(this);
        waitDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        waitDialog.setCancelable(true);
        waitDialog.setCancelMessage(loadDetailsHandler.disposeMessage());
        waitDialog.setMax(count);
        waitDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(final DialogInterface arg0) {
                try {
                    if (loadDetailsThread != null) {
                        loadDetailsThread.stopIt();
                    }
                } catch (final Exception e) {
                    Log.e("CGeoMap.storeCaches.onCancel", e);
                }
            }
        });

        final float etaTime = count * 7.0f / 60.0f;
        final int roundedEta = Math.round(etaTime);
        if (etaTime < 0.4) {
            waitDialog.setMessage(res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm));
        } else {
            waitDialog.setMessage(res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, roundedEta, roundedEta));
        }
        loadDetailsHandler.setStart();

        waitDialog.show();

        loadDetailsThread = new LoadDetails(loadDetailsHandler, geocodes, listIds);
        loadDetailsThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d("NewMap: onDestroy");
        this.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        this.tileCache.destroy();
        this.mapView.getModel().mapViewPosition.destroy();
        this.mapView.destroy();
        ResourceBitmapCacheMonitor.release();

        Routing.disconnect();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.d("New map: onSaveInstanceState");

        final MapState state = prepareMapState();
        outState.putParcelable(BUNDLE_MAP_STATE, state);
        if (historyLayer != null) {
            trailHistory = historyLayer.getHistory();
            outState.putParcelableArrayList(BUNDLE_TRAIL_HISTORY, trailHistory);
        }
    }

    private MapState prepareMapState() {
        return new MapState(MapsforgeUtils.toGeopoint(mapView.getModel().mapViewPosition.getCenter()), mapView.getMapZoomLevel(), followMyLocation, false, targetGeocode, lastNavTarget, mapOptions.isLiveEnabled, mapOptions.isStoredEnabled);
    }

    private void centerMap(final Geopoint geopoint) {
        mapView.getModel().mapViewPosition.setCenter(new LatLong(geopoint.getLatitude(), geopoint.getLongitude()));
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
        myLocSwitch.setChecked(followMyLocation);
        if (followMyLocation) {
            myLocationInMiddle(Sensors.getInstance().currentGeo());
        }
    }

    public void showAddWaypoint(final LatLong tapLatLong) {
        final Geocache cache = getCurrentTargetCache();
        if (cache != null) {
            EditWaypointActivity.startActivityAddWaypoint(this, cache, new Geopoint(tapLatLong.latitude, tapLatLong.longitude));
        }
    }

    @Override
    public Set<String> getCategories(final XmlRenderThemeStyleMenu style) {
        styleMenu = style;
        themeSettingsPref = style.getId();
        final String id = this.sharedPreferences.getString(styleMenu.getId(), styleMenu.getDefaultValue());

        final XmlRenderThemeStyleLayer baseLayer = styleMenu.getLayer(id);
        if (baseLayer == null) {
            Log.w("Invalid style " + id);
            return null;
        }
        final Set<String> result = baseLayer.getCategories();

        // add the categories from overlays that are enabled
        for (final XmlRenderThemeStyleLayer overlay : baseLayer.getOverlays()) {
            if (this.sharedPreferences.getBoolean(overlay.getId(), overlay.isEnabled())) {
                result.addAll(overlay.getCategories());
            }
        }

        return result;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String s) {
        if (StringUtils.equals(s, themeSettingsPref)) {
            AndroidUtil.restartActivity(this);
        }
    }

    // set my location listener
    private static class MyLocationListener implements View.OnClickListener {

        @NonNull
        private final WeakReference<NewMap> mapRef;

        MyLocationListener(@NonNull final NewMap map) {
            mapRef = new WeakReference<>(map);
        }

        private void onFollowMyLocationClicked() {
            followMyLocation = !followMyLocation;
            final NewMap map = mapRef.get();
            if (map != null) {
                map.switchMyLocationButton();
            }
        }

        @Override
        public void onClick(final View view) {
            onFollowMyLocationClicked();
        }
    }

    // Set center of map to my location if appropriate.
    private void myLocationInMiddle(final GeoData geo) {
        if (followMyLocation) {
            centerMap(geo.getCoords());
        }
    }

    private static final class DisplayHandler extends Handler {

        @NonNull
        private final WeakReference<NewMap> mapRef;

        DisplayHandler(@NonNull final NewMap map) {
            this.mapRef = new WeakReference<>(map);
        }

        @Override
        public void handleMessage(final Message msg) {
            final NewMap map = mapRef.get();
            if (map == null) {
                return;
            }

            final int what = msg.what;
            switch (what) {
                case UPDATE_TITLE:
                    map.setTitle();
                    map.setSubtitle();

                    break;
                case INVALIDATE_MAP:
                    map.mapView.repaint();
                    break;

                default:
                    break;
            }
        }

    }

    private void setTitle() {
        final String title = calculateTitle();

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    @NonNull
    private String calculateTitle() {
        if (mapOptions.isLiveEnabled) {
            return res.getString(R.string.map_live);
        }
        if (mapOptions.mapMode == MapMode.SINGLE) {
            final Geocache cache = getSingleModeCache();
            if (cache != null) {
                return cache.getName();
            }
        }
        return StringUtils.defaultIfEmpty(mapOptions.title, res.getString(R.string.map_map));
    }

    private void setSubtitle() {
        final String subtitle = calculateSubtitle();
        if (StringUtils.isEmpty(subtitle)) {
            return;
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(subtitle);
        }
    }

    @NonNull
    private String calculateSubtitle() {
        if (!mapOptions.isLiveEnabled && mapOptions.mapMode == MapMode.SINGLE) {
            final Geocache cache = getSingleModeCache();
            if (cache != null) {
                return Formatter.formatMapSubtitle(cache);
            }
        }

        // count caches in the sub title
        final int visible = countVisibleCaches();
        final int total = countTotalCaches();

        final StringBuilder subtitle = new StringBuilder();
        if (total != 0) {
            if (visible != total && Settings.isDebug()) {
                subtitle.append(visible).append('/').append(res.getQuantityString(R.plurals.cache_counts, total, total));
            } else {
                subtitle.append(res.getQuantityString(R.plurals.cache_counts, visible, visible));
            }
        }

        //        if (Settings.isDebug() && lastSearchResult != null && StringUtils.isNotBlank(lastSearchResult.getUrl())) {
        //            subtitle.append(" [").append(lastSearchResult.getUrl()).append(']');
        //        }

        return subtitle.toString();
    }

    private int countVisibleCaches() {
        return caches != null ? caches.getVisibleCachesCount() : 0;
    }

    private int countTotalCaches() {
        return caches != null ? caches.getCachesCount() : 0;
    }

    /**
     * Updates the progress.
     */
    private static final class ShowProgressHandler extends Handler {
        private int counter = 0;

        @NonNull
        private final WeakReference<NewMap> mapRef;

        ShowProgressHandler(@NonNull final NewMap map) {
            this.mapRef = new WeakReference<>(map);
        }

        @Override
        public void handleMessage(final Message msg) {
            final int what = msg.what;

            if (what == HIDE_PROGRESS) {
                if (--counter == 0) {
                    showProgress(false);
                }
            } else if (what == SHOW_PROGRESS) {
                showProgress(true);
                counter++;
            }
        }

        private void showProgress(final boolean show) {
            final NewMap map = mapRef.get();
            if (map == null) {
                return;
            }
            map.setProgressBarIndeterminateVisibility(show);
        }

    }

    private static final class LoadDetailsHandler extends DisposableHandler {

        private final int detailTotal;
        private int detailProgress;
        private long detailProgressTime;
        private final WeakReference<NewMap> mapRef;

        LoadDetailsHandler(final int detailTotal, final NewMap map) {
            super();

            this.detailTotal = detailTotal;
            this.detailProgress = 0;
            this.mapRef = new WeakReference<>(map);
        }

        public void setStart() {
            detailProgressTime = System.currentTimeMillis();
        }

        @Override
        public void handleRegularMessage(final Message msg) {
            final NewMap map = mapRef.get();
            if (map == null) {
                return;
            }
            if (msg.what == UPDATE_PROGRESS) {
                if (detailProgress < detailTotal) {
                    detailProgress++;
                }
                if (map.waitDialog != null) {
                    final int secondsElapsed = (int) ((System.currentTimeMillis() - detailProgressTime) / 1000);
                    final int secondsRemaining;
                    if (detailProgress > 0) {
                        secondsRemaining = (detailTotal - detailProgress) * secondsElapsed / detailProgress;
                    } else {
                        secondsRemaining = (detailTotal - detailProgress) * secondsElapsed;
                    }

                    map.waitDialog.setProgress(detailProgress);
                    if (secondsRemaining < 40) {
                        map.waitDialog.setMessage(map.res.getString(R.string.caches_downloading) + " " + map.res.getString(R.string.caches_eta_ltm));
                    } else {
                        final int minsRemaining = secondsRemaining / 60;
                        map.waitDialog.setMessage(map.res.getString(R.string.caches_downloading) + " " + map.res.getQuantityString(R.plurals.caches_eta_mins, minsRemaining, minsRemaining));
                    }
                }
            } else if (msg.what == FINISHED_LOADING_DETAILS && map.waitDialog != null) {
                map.waitDialog.dismiss();
                map.waitDialog.setOnCancelListener(null);
            }
        }

        @Override
        public void handleDispose() {
            final NewMap map = mapRef.get();
            if (map == null) {
                return;
            }
            if (map.loadDetailsThread != null) {
                map.loadDetailsThread.stopIt();
            }
        }

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

        @NonNull
        Location currentLocation = Sensors.getInstance().currentGeo();
        float currentHeading;

        private long timeLastPositionOverlayCalculation = 0;
        /**
         * weak reference to the outer class
         */
        @NonNull
        private final WeakReference<NewMap> mapRef;

        UpdateLoc(@NonNull final NewMap map) {
            mapRef = new WeakReference<>(map);
        }

        @Override
        public void updateGeoDir(@NonNull final GeoData geo, final float dir) {
            currentLocation = geo;
            currentHeading = AngleUtils.getDirectionNow(dir);
            repaintPositionOverlay();
        }

        @NonNull
        public Location getCurrentLocation() {
            return currentLocation;
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

                        if (needsRepaintForDistanceOrAccuracy && NewMap.followMyLocation) {
                            map.centerMap(new Geopoint(currentLocation));
                        }

                        if (needsRepaintForDistanceOrAccuracy || needsRepaintForHeading) {

                            map.historyLayer.setCoordinates(currentLocation);
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

    private static class DragHandler implements OnMapDragListener {

        @NonNull
        private final WeakReference<NewMap> mapRef;

        DragHandler(@NonNull final NewMap parent) {
            mapRef = new WeakReference<>(parent);
        }

        @Override
        public void onDrag() {
            final NewMap map = mapRef.get();
            if (map != null && NewMap.followMyLocation) {
                NewMap.followMyLocation = false;
                map.switchMyLocationButton();
            }
        }
    }

    public void showSelection(@NonNull final List<GeoitemRef> items) {
        if (items.isEmpty()) {
            return;
        }

        if (items.size() == 1) {
            showPopup(items.get(0));
            return;
        }
        try {
            final ArrayList<GeoitemRef> sorted = new ArrayList<>(items);
            Collections.sort(sorted, GeoitemRef.NAME_COMPARATOR);

            final LayoutInflater inflater = LayoutInflater.from(this);
            final ListAdapter adapter = new ArrayAdapter<GeoitemRef>(this, R.layout.cacheslist_item_select, sorted) {
                @NonNull
                @Override
                public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {

                    final View view = convertView == null ? inflater.inflate(R.layout.cacheslist_item_select, parent, false) : convertView;
                    final TextView tv = (TextView) view.findViewById(R.id.text);

                    final GeoitemRef item = getItem(position);
                    tv.setText(item.getName());

                    //Put the image on the TextView
                    tv.setCompoundDrawablesWithIntrinsicBounds(item.getMarkerId(), 0, 0, 0);

                    final TextView infoView = (TextView) view.findViewById(R.id.info);
                    final StringBuilder text = new StringBuilder(item.getItemCode());
                    if (item.getType() == CoordinatesType.WAYPOINT && StringUtils.isNotEmpty(item.getGeocode())) {
                        text.append(Formatter.SEPARATOR).append(item.getGeocode());
                        final Geocache cache = DataStore.loadCache(item.getGeocode(), LoadFlags.LOAD_CACHE_OR_DB);
                        if (cache != null) {
                            text.append(Formatter.SEPARATOR).append(cache.getName());
                        }
                    }
                    infoView.setText(text.toString());

                    return view;
                }
            };

            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(res.getString(R.string.map_select_multiple_items))
                    .setAdapter(adapter, new SelectionClickListener(sorted))
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

        } catch (final NotFoundException e) {
            Log.e("NewMap.showSelection", e);
        }
    }

    private class SelectionClickListener implements DialogInterface.OnClickListener {

        @NonNull
        private final List<GeoitemRef> items;

        SelectionClickListener(@NonNull final List<GeoitemRef> items) {
            this.items = items;
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            if (which >= 0 && which < items.size()) {
                final GeoitemRef item = items.get(which);
                showPopup(item);
            }
        }

    }

    private void showPopup(final GeoitemRef item) {
        if (item == null || StringUtils.isEmpty(item.getGeocode())) {
            return;
        }

        try {
            if (item.getType() == CoordinatesType.CACHE) {
                final Geocache cache = DataStore.loadCache(item.getGeocode(), LoadFlags.LOAD_CACHE_OR_DB);
                if (cache != null) {
                    final RequestDetailsThread requestDetailsThread = new RequestDetailsThread(cache, this);
                    requestDetailsThread.start();
                    return;
                }
                return;
            }

            if (item.getType() == CoordinatesType.WAYPOINT && item.getId() >= 0) {
                popupGeocodes.add(item.getGeocode());
                WaypointPopup.startActivityAllowTarget(this, item.getId(), item.getGeocode());
            }

        } catch (final NotFoundException e) {
            Log.e("NewMap.showPopup", e);
        }
    }

    @Nullable
    private Geocache getSingleModeCache() {
        if (StringUtils.isNotBlank(mapOptions.geocode)) {
            return DataStore.loadCache(mapOptions.geocode, LoadFlags.LOAD_CACHE_OR_DB);
        }

        return null;
    }

    @Nullable
    private Geocache getCurrentTargetCache() {
        if (StringUtils.isNotBlank(targetGeocode)) {
            return DataStore.loadCache(targetGeocode, LoadFlags.LOAD_CACHE_OR_DB);
        }

        return null;
    }

    private void savePrefs() {
        Settings.setMapZoom(MapMode.SINGLE, mapView.getMapZoomLevel());
        Settings.setMapCenter(new MapsforgeGeoPoint(mapView.getModel().mapViewPosition.getCenter()));
    }

    private static class RequestDetailsThread extends Thread {

        @NonNull
        private final Geocache cache;
        @NonNull
        private final WeakReference<NewMap> mapRef;

        RequestDetailsThread(@NonNull final Geocache cache, @NonNull final NewMap map) {
            this.cache = cache;
            this.mapRef = new WeakReference<>(map);
        }

        public boolean requestRequired() {
            return CacheType.UNKNOWN == cache.getType() || cache.getDifficulty() == 0;
        }

        @Override
        public void run() {
            final NewMap map = this.mapRef.get();
            if (map == null) {
                return;
            }
            if (requestRequired()) {
                try {
                    /* final SearchResult search = */
                    GCMap.searchByGeocodes(Collections.singleton(cache.getGeocode()));
                } catch (final Exception ex) {
                    Log.w("Error requesting cache popup info", ex);
                    ActivityMixin.showToast(map, R.string.err_request_popup_info);
                }
            }
            map.popupGeocodes.add(cache.getGeocode());
            CachePopup.startActivityAllowTarget(map, cache.getGeocode());
        }
    }

    /**
     * Thread to store the caches in the viewport. Started by Activity.
     */

    private class LoadDetails extends Thread {

        private final DisposableHandler handler;
        private final Collection<String> geocodes;
        private final Set<Integer> listIds;

        LoadDetails(final DisposableHandler handler, final Collection<String> geocodes, final Set<Integer> listIds) {
            this.handler = handler;
            this.geocodes = geocodes;
            this.listIds = listIds;
        }

        public void stopIt() {
            handler.dispose();
        }

        @Override
        public void run() {
            if (CollectionUtils.isEmpty(geocodes)) {
                return;
            }

            for (final String geocode : geocodes) {
                try {
                    if (handler.isDisposed()) {
                        break;
                    }

                    if (!DataStore.isOffline(geocode, null)) {
                        Geocache.storeCache(null, geocode, listIds, false, handler);
                    }
                } catch (final Exception e) {
                    Log.e("CGeoMap.LoadDetails.run", e);
                } finally {
                    handler.sendEmptyMessage(UPDATE_PROGRESS);
                }
            }

            // we're done, but map might even have been closed.
            if (caches != null) {
                caches.invalidate(geocodes);
            }
            invalidateOptionsMenuCompatible();
            handler.sendEmptyMessage(FINISHED_LOADING_DETAILS);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AbstractDialogFragment.REQUEST_CODE_TARGET_INFO) {
            if (resultCode == AbstractDialogFragment.RESULT_CODE_SET_TARGET) {
                final TargetInfo targetInfo = data.getExtras().getParcelable(Intents.EXTRA_TARGET_INFO);

                if (targetInfo != null) {
                    lastNavTarget = targetInfo.coords;
                    if (navigationLayer != null) {
                        navigationLayer.setDestination(targetInfo.coords);
                        navigationLayer.requestRedraw();
                    }
                    if (distanceView != null) {
                        distanceView.setDestination(targetInfo.coords);
                        distanceView.setCoordinates(geoDirUpdate.getCurrentLocation());
                    }
                    if (StringUtils.isNotBlank(targetInfo.geocode)) {
                        targetGeocode = targetInfo.geocode;
                        final Geocache target = getCurrentTargetCache();
                        targetView.setTarget(targetGeocode, target != null ? target.getName() : StringUtils.EMPTY);
                    }
                }
            }
            final List<String> changedGeocodes = new ArrayList<>();
            String geocode = popupGeocodes.poll();
            while (geocode != null) {
                changedGeocodes.add(geocode);
                geocode = popupGeocodes.poll();
            }
            if (caches != null) {
                caches.invalidate(changedGeocodes);
            }
        }
    }

    private static class ResourceBitmapCacheMonitor {

        private static int refCount = 0;

        static synchronized void addRef() {
            refCount++;
            Log.d("ResourceBitmapCacheMonitor.addRef");
        }

        static synchronized void release() {
            if (refCount > 0) {
                refCount--;
                Log.d("ResourceBitmapCacheMonitor.release");
                if (refCount == 0) {
                    Log.d("ResourceBitmapCacheMonitor.clearResourceBitmaps");
                    AndroidResourceBitmap.clearResourceBitmaps();
                }
            }
        }

    }
}
