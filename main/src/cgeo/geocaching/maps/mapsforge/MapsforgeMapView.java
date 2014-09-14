package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.geopoint.Viewport;
import cgeo.geocaching.maps.CachesOverlay;
import cgeo.geocaching.maps.PositionAndScaleOverlay;
import cgeo.geocaching.maps.interfaces.GeneralOverlay;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapControllerImpl;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;
import cgeo.geocaching.maps.interfaces.MapViewImpl;
import cgeo.geocaching.maps.interfaces.OnMapDragListener;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

import org.eclipse.jdt.annotation.NonNull;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import java.io.File;
public class MapsforgeMapView extends MapView implements MapViewImpl {
    private GestureDetector gestureDetector;
    private OnMapDragListener onDragListener;
    private final MapsforgeMapController mapController = new MapsforgeMapController(getModel(), getModel().mapViewPosition.getZoomLevelMax());
    private final TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    public MapsforgeMapView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
        // create a tile cache of suitable size
        this.tileCache = AndroidUtil.createTileCache(context, "mapcache",
                this.getModel().displayModel.getTileSize(), 1f,
                this.getModel().frameBufferModel.getOverdrawFactor());
    }

    private void initialize(final Context context) {

        if (isInEditMode()) {
            return;
        }
        gestureDetector = new GestureDetector(context, new GestureListener());
        if (Settings.isScaleMapsforgeText()) {
            //this.setTextScale(getResources().getDisplayMetrics().density);
        }
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        try {
            // Google Maps and OSM Maps use different zoom levels for the same view.
            // Here we don't want the Google Maps compatible zoom level, but the actual one.
            if (getActualMapZoomLevel() > 22) { // to avoid too close zoom level (mostly on Samsung Galaxy S series)
                mapController.setZoom(22);
            }

            super.draw(canvas);
        } catch (final Exception e) {
            Log.e("MapsforgeMapView.draw", e);
        }
    }

    @Override
    public void displayZoomControls(final boolean takeFocus) {
        // nothing to do here
    }

    @Override
    public MapControllerImpl getMapController() {
        return mapController;
    }

    @Override
    @NonNull
    public GeoPointImpl getMapViewCenter() {
        final LatLong point = getModel().mapViewPosition.getCenter();
        return new MapsforgeGeoPoint(point.latitude, point.longitude);
    }

    @Override
    public Viewport getViewport() {
        return new Viewport(getMapViewCenter(), getLatitudeSpan() / 1e6, getLongitudeSpan() / 1e6);
    }

    @Override
    public void clearOverlays() {
        getLayerManager().getLayers().clear();
    }

    @Override
    public MapProjectionImpl getMapProjection() {
        return new MapsforgeMapProjection(/* getProjection() */);
    }

    @Override
    public CachesOverlay createAddMapOverlay(final Context context, final Drawable drawable) {

        final MapsforgeCacheOverlay ovl = new MapsforgeCacheOverlay(context, drawable);
        //        getOverlays().add(ovl);
        return ovl.getBase();
    }

    @Override
    public PositionAndScaleOverlay createAddPositionAndScaleOverlay() {
        final MapsforgeOverlay ovl = new MapsforgeOverlay();
        //        getOverlays().add(ovl);
        return (PositionAndScaleOverlay) ovl.getBase();
    }

    @Override
    public int getLatitudeSpan() {

        final BoundingBox maplimit = getModel().mapViewPosition.getMapLimit();

        if (maplimit == null) {
            return 0;
        }

        return (int) Math.round(maplimit.getLatitudeSpan() * 1e6);

        //        int span = 0;
        //
        //        final Projection projection = getProjection();
        //
        //        if (projection != null && getHeight() > 0) {
        //
        //            final GeoPoint low = projection.fromPixels(0, 0);
        //            final GeoPoint high = projection.fromPixels(0, getHeight());
        //
        //            if (low != null && high != null) {
        //                span = Math.abs(high.latitudeE6 - low.latitudeE6);
        //            }
        //        }
        //
        //        return span;
    }

    @Override
    public int getLongitudeSpan() {

        final BoundingBox maplimit = getModel().mapViewPosition.getMapLimit();

        if (maplimit == null) {
            return 0;
        }

        return (int) Math.round(maplimit.getLongitudeSpan() * 1e6);

        //        int span = 0;
        //
        //        final Projection projection = getProjection();
        //
        //        if (projection != null && getWidth() > 0) {
        //            final GeoPoint low = projection.fromPixels(0, 0);
        //            final GeoPoint high = projection.fromPixels(getWidth(), 0);
        //
        //            if (low != null && high != null) {
        //                span = Math.abs(high.longitudeE6 - low.longitudeE6);
        //            }
        //        }
        //
        //        return span;
    }

    @Override
    public void preLoad() {
        // Nothing to do here
    }

    /**
     * Get the map zoom level which is compatible with Google Maps.
     *
     * @return the current map zoom level +1
     */
    @Override
    public int getMapZoomLevel() {
        // Google Maps and OSM Maps use different zoom levels for the same view.
        // All OSM Maps zoom levels are offset by 1 so they match Google Maps.
        return getModel().mapViewPosition.getZoomLevel() + 1;
    }

    /**
     * Get the actual map zoom level
     *
     * @return the current map zoom level with no adjustments
     */
    private int getActualMapZoomLevel() {
        return getModel().mapViewPosition.getZoomLevel();
    }

    @Override
    public void setMapSource() {

        //        this.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        //        this.getModel().mapViewPosition.setZoomLevel((byte) 12);

        // tile renderer layer using internal render theme
        this.tileRendererLayer = new TileRendererLayer(tileCache,
                this.getModel().mapViewPosition, false, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setMapFile(new File(Settings.getMapFile()));
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        // only once a layer is associated with a mapView the rendering starts
        this.getLayerManager().getLayers().add(tileRendererLayer);

        //        MapGeneratorInternal newMapType = MapGeneratorInternal.MAPNIK;
        //        final MapSource mapSource = Settings.getMapSource();
        //        if (mapSource instanceof MapsforgeMapSource) {
        //            newMapType = ((MapsforgeMapSource) mapSource).getGenerator();
        //        }
        //
        //        final MapGenerator mapGenerator = MapGeneratorFactory.createMapGenerator(newMapType);
        //
        //        // When swapping map sources, make sure we aren't exceeding max zoom. See bug #1535
        //        final int maxZoom = mapGenerator.getZoomLevelMax();
        //        if (getMapPosition().getZoomLevel() > maxZoom) {
        //            getController().setZoom(maxZoom);
        //        }
        //
        //        setMapGenerator(mapGenerator);
        //        if (!mapGenerator.requiresInternetConnection()) {
        //            if (!new File(Settings.getMapFile()).exists()) {
        //                Toast.makeText(
        //                        getContext(),
        //                        getContext().getResources().getString(R.string.warn_nonexistant_mapfile),
        //                        Toast.LENGTH_LONG)
        //                        .show();
        //                return;
        //            }
        //            setMapFile(new File(Settings.getMapFile()));
        //            if (!Settings.isValidMapFile(Settings.getMapFile())) {
        //                Toast.makeText(
        //                        getContext(),
        //                        getContext().getResources().getString(R.string.warn_invalid_mapfile),
        //                        Toast.LENGTH_LONG)
        //                        .show();
        //            }
        //        }
        //        if (hasMapThemes()) {
        //            setMapTheme();
        //        }
    }

    @Override
    public boolean hasMapThemes() {
        return false;
        //        return !getMapGenerator().requiresInternetConnection();
    }

    @Override
    public void setMapTheme() {
        //        final String customRenderTheme = Settings.getCustomRenderThemeFilePath();
        //        if (StringUtils.isNotEmpty(customRenderTheme)) {
        //            try {
        //                setRenderTheme(new File(customRenderTheme));
        //            } catch (final FileNotFoundException ignored) {
        //                Toast.makeText(
        //                        getContext(),
        //                        getContext().getResources().getString(R.string.warn_rendertheme_missing),
        //                        Toast.LENGTH_LONG)
        //                        .show();
        //            }
        //        } else {
        //            setRenderTheme(DEFAULT_RENDER_THEME);
        //        }
    }

    @Override
    public void repaintRequired(final GeneralOverlay overlay) {

        //        if (null == overlay) {
            invalidate();
        //        } else {
        //            try {
        //                final Overlay ovl = (Overlay) overlay.getOverlayImpl();
        //
        //                if (ovl != null) {
        //                    //                    ovl.requestRedraw();
        //                }
        //
        //            } catch (final Exception e) {
        //                Log.e("MapsforgeMapView.repaintRequired", e);
        //            }
        //        }
    }

    @Override
    public void setOnDragListener(final OnMapDragListener onDragListener) {
        this.onDragListener = onDragListener;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.onTouchEvent(ev);
    }

    private class GestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            if (onDragListener != null) {
                onDragListener.onDrag();
            }
            return true;
        }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2,
                final float distanceX, final float distanceY) {
            if (onDragListener != null) {
                onDragListener.onDrag();
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }
    }

    @Override
    public boolean needsInvertedColors() {
        return false;
    }
}
