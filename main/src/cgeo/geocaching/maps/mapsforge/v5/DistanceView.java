package cgeo.geocaching.maps.mapsforge.v5;

import butterknife.Bind;

import cgeo.geocaching.R;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;

import android.location.Location;
import android.view.View;
import android.widget.TextView;

public class DistanceView {

    private final Geopoint destinationCoords;

    @Bind(R.id.distance) protected TextView distanceView;

    public DistanceView(final Geopoint destinationCoords, final TextView distanceView) {

        this.destinationCoords = destinationCoords;
        this.distanceView = distanceView;

        this.distanceView.setVisibility(destinationCoords != null ? View.VISIBLE : View.GONE);
    }

    public void setCoordinates(final Location coordinatesIn) {
        if (destinationCoords == null || coordinatesIn == null) {
            return;
        }

        final Geopoint currentCoords = new Geopoint(coordinatesIn);

        final float distance = currentCoords.distanceTo(destinationCoords);
        distanceView.setText(Units.getDistanceFromKilometers(distance));
    }
}
