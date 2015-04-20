package cgeo.geocaching.maps.mapsforge.v5;

import org.mapsforge.map.android.view.MapView;

import android.content.Context;
import android.util.AttributeSet;

public class MfMapView extends MapView {

    public MfMapView(final Context context, final AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public double getLongitudeSpan() {

        return getModel().mapViewPosition.getMapLimit().getLongitudeSpan();
    }

    public double getLatitudeSpan() {

        return getModel().mapViewPosition.getMapLimit().getLatitudeSpan();
    }

}
