package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Matrix;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.Layer;

import android.graphics.drawable.Drawable;
import android.location.Location;

public class PositionLayer extends Layer {

    private Location coordinates = null;
    private LatLong location = null;
    private float heading = 0f;
    private Bitmap arrow = null;
    private int widthArrowHalf = 0;
    private int heightArrowHalf = 0;

    @Override
    public void draw(final BoundingBox boundingBox, final byte zoomLevel, final Canvas canvas, final Point topLeftPoint) {

        if (arrow == null) {
            final Drawable myloc = CgeoApplication.getInstance().getResources().getDrawable(R.drawable.my_location_chevron);
            arrow = AndroidGraphicFactory.convertToBitmap(myloc);
            widthArrowHalf = arrow.getWidth() / 2;
            heightArrowHalf = arrow.getHeight() / 2;
        }

        final long mapSize = MercatorProjection.getMapSize(zoomLevel, this.displayModel.getTileSize());
        final double pixelX = MercatorProjection.longitudeToPixelX(this.location.longitude, mapSize);
        final double pixelY = MercatorProjection.latitudeToPixelY(this.location.latitude, mapSize);

        final int left = (int) (pixelX - topLeftPoint.x - widthArrowHalf);
        final int top = (int) (pixelY - topLeftPoint.y - heightArrowHalf);
        final int right = left + this.arrow.getWidth();
        final int bottom = top + this.arrow.getHeight();
        final Rectangle bitmapRectangle = new Rectangle(left, top, right, bottom);
        final Rectangle canvasRectangle = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        if (!canvasRectangle.intersects(bitmapRectangle)) {
            return;
        }
        final Matrix matrix = AndroidGraphicFactory.INSTANCE.createMatrix();
        matrix.rotate(heading, widthArrowHalf, heightArrowHalf);
        matrix.translate(left, top);

        canvas.drawBitmap(arrow, matrix);

    }

    public void setHeading(final float bearingNow) {
        heading = bearingNow;
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
