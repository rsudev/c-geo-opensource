package cgeo.geocaching.maps.mapsforge.v5;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TapHandler {

    private final WeakReference<NewMap> map;
    private final Set<GeoitemRef> hitItems = new HashSet<>();

    public TapHandler(final NewMap map) {
        this.map = new WeakReference<>(map);
    }

    public synchronized void setHit(final GeoitemRef item) {
        this.hitItems.add(item);
    }

    public synchronized void finished() {

        final NewMap map = this.map.get();

        // show popup
        if (map != null) {
            map.showSelection(new ArrayList<>(hitItems));
        }

        hitItems.clear();
    }
}
