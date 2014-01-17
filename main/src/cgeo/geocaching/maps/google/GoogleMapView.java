package cgeo.geocaching.maps.google;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import cgeo.geocaching.geopoint.Viewport;
import cgeo.geocaching.maps.CachesOverlay;
import cgeo.geocaching.maps.PositionAndScaleOverlay;
import cgeo.geocaching.maps.interfaces.GeneralOverlay;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapControllerImpl;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;
import cgeo.geocaching.maps.interfaces.MapViewImpl;
import cgeo.geocaching.maps.interfaces.OnMapDragListener;
import cgeo.geocaching.maps.interfaces.OverlayImpl;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import org.apache.commons.lang3.reflect.MethodUtils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ZoomButtonsController;

import java.util.ArrayList;
import java.util.List;

public class GoogleMapView extends MapView implements MapViewImpl {
    private GestureDetector gestureDetector;
    private OnMapDragListener onDragListener;
    private final GoogleMapController mapController;
    private final List<OverlayImpl> overlays = new ArrayList<OverlayImpl>();

    public GoogleMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureListener());
        mapController = new GoogleMapController(getMap());
    }

    public GoogleMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gestureDetector = new GestureDetector(context, new GestureListener());
        mapController = new GoogleMapController(getMap());
    }

    public GoogleMapView(Context context) {
        super(context);
        gestureDetector = new GestureDetector(context, new GestureListener());
        mapController = new GoogleMapController(getMap());
    }

    @Override
    public void draw(final Canvas canvas) {
        try {
            if (getMapZoomLevel() > 22) { // to avoid too close zoom level (mostly on Samsung Galaxy S series)
                mapController.setZoom(22);
            }

            super.draw(canvas);
        } catch (Exception e) {
            Log.e("GoogleMapView.draw", e);
        }
    }

    @Override
    public void displayZoomControls(boolean takeFocus) {
        try {
            // Push zoom controls to the right
            FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            zoomParams.gravity = Gravity.RIGHT;
            // The call to retrieve the zoom buttons controller is undocumented and works so far on all devices
            // supported by Google Play, but fails at least on one Jolla.
            final ZoomButtonsController controller = (ZoomButtonsController) MethodUtils.invokeMethod(this, "getZoomButtonsController");
            controller.getZoomControls().setLayoutParams(zoomParams);

            //super.displayZoomControls(takeFocus);
        } catch (NoSuchMethodException e) {
            Log.w("GoogleMapView.displayZoomControls: unable to explicitly place the zoom buttons");
        } catch (Exception e) {
            Log.e("GoogleMapView.displayZoomControls", e);
        }
    }

    @Override
    public MapControllerImpl getMapController() {
        return mapController;
    }

    @Override
    public GeoPointImpl getMapViewCenter() {
        LatLng point = mapController.getMapCenter();
        return new GoogleGeoPoint(point);
    }

    @Override
    public Viewport getViewport() {
        return mapController.getViewport();
    }

    @Override
    public void clearOverlays() {
        overlays.clear();
    }

    @Override
    public MapProjectionImpl getMapProjection() {
        return new GoogleMapProjection(getMap().getProjection());
    }

    @Override
    public CachesOverlay createAddMapOverlay(Context context, Drawable drawable) {

        GoogleCacheOverlay ovl = new GoogleCacheOverlay(context, drawable);
        overlays.add(ovl);
        return ovl.getBase();
    }

    @Override
    public PositionAndScaleOverlay createAddPositionAndScaleOverlay(Activity activity) {

        GoogleOverlay ovl = new GoogleOverlay(activity);
        overlays.add(ovl);
        return (PositionAndScaleOverlay) ovl.getBase();
    }

    @Override
    public int getMapZoomLevel() {
        return Math.round(getMap().getCameraPosition().zoom);
    }

    @Override
    public void setMapSource() {
        if (GoogleMapProvider.isSatelliteSource(Settings.getMapSource())) {
            getMap().setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else {
            getMap().setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    @Override
    public void repaintRequired(GeneralOverlay overlay) {
        invalidate();
    }

    @Override
    public void setOnDragListener(OnMapDragListener onDragListener) {
        this.onDragListener = onDragListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            gestureDetector.onTouchEvent(ev);
            return super.onTouchEvent(ev);
        } catch (Exception e) {
            Log.e("GoogleMapView.onTouchEvent", e);
        }
        return false;
    }

    private class GestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mapController.zoomInFixing((int) e.getX(), (int) e.getY());
            if (onDragListener != null) {
                onDragListener.onDrag();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
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

    @Override
    public boolean hasMapThemes() {
        // Not supported
        return false;
    }

    @Override
    public void setMapTheme() {
        // Not supported
    }

    @Override
    public void setBuiltInZoomControls(boolean b) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getLatitudeSpan() {
        return mapController.getLatitudeSpan();
    }

    @Override
    public int getLongitudeSpan() {
        return mapController.getLongitudeSpan();
    }
}
