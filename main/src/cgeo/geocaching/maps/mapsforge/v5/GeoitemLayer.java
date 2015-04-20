package cgeo.geocaching.maps.mapsforge.v5;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.layer.overlay.Marker;

public class GeoitemLayer extends Marker {

    private final String geocode;

    public GeoitemLayer(final String geocode, final LatLong latLong, final Bitmap bitmap, final int horizontalOffset, final int verticalOffset) {
        super(latLong, bitmap, horizontalOffset, verticalOffset);

        this.geocode = geocode;
    }

    public String getGeocode() {
        return geocode;
    }

}
