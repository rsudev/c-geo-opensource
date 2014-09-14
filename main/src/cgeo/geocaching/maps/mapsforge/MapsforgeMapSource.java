package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.maps.AbstractMapSource;
import cgeo.geocaching.maps.interfaces.MapProvider;

class MapsforgeMapSource extends AbstractMapSource {

    //    private final MapGeneratorInternal generator;

    public MapsforgeMapSource(final String id, final MapProvider mapProvider, final String name/*
                                                                                                * , MapGeneratorInternal
                                                                                                * generator
                                                                                                */) {
        super(id, mapProvider, name);
        //        this.generator = generator;
    }

    //    public MapGeneratorInternal getGenerator() {
    //        return generator;
    //    }

}