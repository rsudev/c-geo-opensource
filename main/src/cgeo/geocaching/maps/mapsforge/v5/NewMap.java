package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.Intents;
import cgeo.geocaching.R;
import cgeo.geocaching.activity.AbstractActionBarActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.maps.CGeoMap.MapMode;
import cgeo.geocaching.settings.Settings;

import org.apache.commons.lang3.StringUtils;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidResourceBitmap;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.io.File;

public class NewMap extends AbstractActionBarActivity {

    private MfMapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidGraphicFactory.createInstance(this.getApplication());

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

        this.mapView.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        // tile renderer layer using internal render theme
        this.tileRendererLayer = new TileRendererLayer(tileCache,
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setMapFile(NewMap.getMapFile());
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        // only once a layer is associated with a mapView the rendering starts
        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);
    }

    @Override
    protected void onStop() {
        super.onStop();

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

}
