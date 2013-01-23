package cgeo.geocaching.connector;

import cgeo.geocaching.ICache;
import cgeo.geocaching.cgCache;

import org.apache.commons.lang3.StringUtils;

public class GeopeitusConnector extends AbstractConnector {

    @Override
    public String getName() {
        return "geopeitus.ee";
    }

    @Override
    public String getCacheUrl(final cgCache cache) {
        return getCacheUrlPrefix() + StringUtils.stripStart(cache.getGeocode().substring(2), "0");
    }

    @Override
    public String getHost() {
        return "www.geopeitus.ee";
    }

    @Override
    public boolean isOwner(final ICache cache) {
        return false;
    }

    @Override
    public boolean canHandle(String geocode) {
        return StringUtils.startsWith(geocode, "GE") && isNumericId(geocode.substring(2));
    }

    @Override
    protected String getCacheUrlPrefix() {
        return "http://" + getHost() + "/aare/";
    }
}
