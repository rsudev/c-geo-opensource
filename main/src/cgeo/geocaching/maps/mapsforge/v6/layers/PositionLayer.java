package cgeo.geocaching.maps.mapsforge.v6.layers;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;

import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;

public class PositionLayer extends Layer {

    private Location coordinates = null;
    private LatLong location = null;
    private float heading = 0f;
    private android.graphics.Bitmap arrowNative = null;
    private android.graphics.Bitmap arrowRotNative = null;
    private Bitmap arrow = null;
    private Paint accuracyCircle = null;
    private int widthArrowHalf = 0;
    private int heightArrowHalf = 0;

    @Override
    public void draw(final BoundingBox boundingBox, final byte zoomLevel, final Canvas canvas, final Point topLeftPoint) {

        if (coordinates == null) {
            return;
        }

        final float accuracy = coordinates.getAccuracy();

        final long mapSize = MercatorProjection.getMapSize(zoomLevel, this.displayModel.getTileSize());
        final double pixelX = MercatorProjection.longitudeToPixelX(this.location.longitude, mapSize);
        final double pixelY = MercatorProjection.latitudeToPixelY(this.location.latitude, mapSize);

        final int center_x = (int) (pixelX - topLeftPoint.x);
        final int center_y = (int) (pixelY - topLeftPoint.y);

        final int radius = (int) MercatorProjection.metersToPixelsWithScaleFactor(accuracy, location.latitude,
                this.displayModel.getScaleFactor(), this.displayModel.getTileSize());

        if (accuracyCircle == null) {
            accuracyCircle = AndroidGraphicFactory.INSTANCE.createPaint();
            accuracyCircle.setStrokeWidth(1.0f);
        }

        accuracyCircle.setColor(0x66000000);
        accuracyCircle.setStyle(Style.STROKE);
        canvas.drawCircle(center_x, center_y, radius, accuracyCircle);

        accuracyCircle.setColor(0x08000000);
        accuracyCircle.setStyle(Style.FILL);
        canvas.drawCircle(center_x, center_y, radius, accuracyCircle);

        if (arrow == null) {
            arrowNative = BitmapFactory.decodeResource(CgeoApplication.getInstance().getResources(), R.drawable.my_location_chevron);

            rotateArrow();
        }


        final int left = center_x - widthArrowHalf;
        final int top = center_y - heightArrowHalf;
        final int right = left + this.arrow.getWidth();
        final int bottom = top + this.arrow.getHeight();
        final Rectangle bitmapRectangle = new Rectangle(left, top, right, bottom);
        final Rectangle canvasRectangle = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        if (!canvasRectangle.intersects(bitmapRectangle)) {
            return;
        }

        canvas.drawBitmap(arrow, left, top);

    }

    private void rotateArrow() {

        if (arrowNative == null) {
            return;
        }

        final Matrix matrix = new Matrix();
        matrix.setRotate(heading, widthArrowHalf, heightArrowHalf);
        arrowRotNative = android.graphics.Bitmap.createBitmap(arrowNative, 0, 0, arrowNative.getWidth(), arrowNative.getHeight(), matrix, true);

        final Drawable tmpArrow = new BitmapDrawable(CgeoApplication.getInstance().getResources(), arrowRotNative);
        arrow = AndroidGraphicFactory.convertToBitmap(tmpArrow);

        widthArrowHalf = arrow.getWidth() / 2;
        heightArrowHalf = arrow.getHeight() / 2;
    }

    public void setHeading(final float bearingNow) {
        if (heading != bearingNow) {

            heading = bearingNow;

            rotateArrow();
        }
    }

    public float getHeading() {
        return heading;
    }

    public void setCoordinates(final Location coordinatesIn) {
        coordinates = coordinatesIn;
        location = new LatLong(coordinatesIn.getLatitude(), coordinatesIn.getLongitude());
    }

    public Location getCoordinates() {
        return coordinates;
    }

}
