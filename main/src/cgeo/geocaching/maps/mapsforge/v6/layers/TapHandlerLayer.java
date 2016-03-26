package cgeo.geocaching.maps.mapsforge.v6.layers;

import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.layer.Layer;

import cgeo.geocaching.maps.mapsforge.v6.TapHandler;

public class TapHandlerLayer extends Layer {

    private final TapHandler tapHandler;

    public TapHandlerLayer(final TapHandler tapHandler) {
        this.tapHandler = tapHandler;
    }

    @Override
    public void draw(final BoundingBox arg0, final byte arg1, final Canvas arg2, final Point arg3) {
        // nothing visible here
    }

    @Override
    public boolean onTap(final LatLong tapLatLong, final Point layerXY, final Point tapXY) {

        tapHandler.finished();

        return true;
    }

    @Override
    public boolean onLongPress(LatLong tapLatLong, Point layerXY, Point tapXY) {

        tapHandler.onLongPress(tapLatLong);

        return true;
    }
}
