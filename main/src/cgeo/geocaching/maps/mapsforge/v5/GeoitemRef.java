package cgeo.geocaching.maps.mapsforge.v5;

import cgeo.geocaching.enumerations.CoordinatesType;

import org.apache.commons.lang3.StringUtils;

public class GeoitemRef {

    private final String itemCode;
    private final CoordinatesType type;
    private final String geocode;
    private final int id;

    public GeoitemRef(final String itemCode, final CoordinatesType type, final String geocode, final int id) {
        this.itemCode = itemCode;
        this.type = type;
        this.geocode = geocode;
        this.id = id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoitemRef)) {
            return false;
        }
        return (StringUtils.equalsIgnoreCase(this.itemCode, ((GeoitemRef) o).itemCode));
    }

    @Override
    public int hashCode() {
        return StringUtils.defaultString(itemCode).hashCode();
    }

    @Override
    public String toString() {
        return itemCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public CoordinatesType getType() {
        return type;
    }

    public String getGeocode() {
        return geocode;
    }

    public int getId() {
        return id;
    }
}
