package cgeo.geocaching.maps.mapsforge.v5;

import org.mapsforge.map.layer.Layer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public class GeoitemLayers implements Collection<GeoitemLayer> {

    final private HashMap<String, GeoitemLayer> geoitems = new HashMap<>();

    public Collection<String> getGeocodes() {
        return geoitems.keySet();
    }

    public Collection<Layer> getAsLayers() {
        return new ArrayList<Layer>(this.geoitems.values());
    }

    @Override
    public boolean add(final GeoitemLayer geoitem) {
        return geoitems.put(geoitem.getGeocode(), geoitem) != null;
    }

    @Override
    public boolean addAll(final Collection<? extends GeoitemLayer> geoitems) {
        boolean result = true;
        for (final GeoitemLayer geoitem : geoitems) {
            if (!this.add(geoitem)) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public void clear() {
        this.geoitems.clear();
    }

    @Override
    public boolean contains(final Object object) {
        return this.geoitems.containsValue(object);
    }

    @Override
    public boolean containsAll(final Collection<?> items) {
        return this.geoitems.values().containsAll(items);
    }

    @Override
    public boolean isEmpty() {
        return this.geoitems.isEmpty();
    }

    @Override
    public Iterator<GeoitemLayer> iterator() {
        return this.geoitems.values().iterator();
    }

    @Override
    public boolean remove(final Object object) {
        if (object instanceof GeoitemLayer) {
            final GeoitemLayer item = (GeoitemLayer) object;
            return this.geoitems.remove(item.getGeocode()) != null;

        }

        return false;
    }

    @Override
    public boolean removeAll(final Collection<?> items) {
        boolean result = true;
        for (final Object item : items) {
            if (!this.remove(item)) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public boolean retainAll(final Collection<?> items) {

        return this.geoitems.values().retainAll(items);
    }

    @Override
    public int size() {

        return this.geoitems.size();
    }

    @Override
    public Object[] toArray() {

        return this.geoitems.values().toArray();
    }

    @Override
    public <T> T[] toArray(final T[] array) {

        return this.geoitems.values().toArray(array);
    }

}
