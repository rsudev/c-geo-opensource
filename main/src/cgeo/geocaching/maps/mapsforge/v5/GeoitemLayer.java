package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.map.layer.overlay.Marker;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class GeoitemLayer extends Marker {

    private final static double tapSpanInches = 0.12; // 3mm as inches
    private final static double tapSpanRadius;

    static {

        final DisplayMetrics metrics = new DisplayMetrics();
        final WindowManager windowManager = (WindowManager) CgeoApplication.getInstance().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        tapSpanRadius = metrics.densityDpi * tapSpanInches / 2.0;
    }

    private final String geocode;
    private final TapHandler tapHandler;
    private final double halfXSpan;
    private final double halfYSpan;

    public GeoitemLayer(final String geocode, final TapHandler tapHandler, final LatLong latLong, final Bitmap bitmap, final int horizontalOffset, final int verticalOffset) {
        super(latLong, bitmap, horizontalOffset, verticalOffset);

        this.geocode = geocode;
        this.tapHandler = tapHandler;
        this.halfXSpan = getBitmap().getWidth() / 2.0;
        this.halfYSpan = getBitmap().getHeight() / 2.0;
    }

    public String getGeocode() {
        return geocode;
    }

    @Override
    public boolean onTap(final LatLong tapLatLong, final Point layerXY, final Point tapXY) {
        if (isHit(layerXY, tapXY)) {
            tapHandler.setHit(geocode);
        }
        return super.onTap(tapLatLong, layerXY, tapXY);
    }

    private boolean isHit(final Point layerXY, final Point tapXY) {
        final Rectangle rect = new Rectangle(layerXY.x + getHorizontalOffset() - halfXSpan, layerXY.y + getVerticalOffset() - halfYSpan, layerXY.x + getHorizontalOffset() + halfXSpan, layerXY.y + getVerticalOffset() + halfYSpan);

        return rect.intersectsCircle(tapXY.x, tapXY.y, tapSpanRadius);
    }

}
