package com.example.mapsmcp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderType {
    GOOGLE("google"),
    OPENSTREETMAP("openstreetmap"),
    NOMINATIM("nominatim"),
    OSRM("osrm"),
    MAPBOX("mapbox"),
    GRAPHHOPPER("graphhopper"),
    OPENROUTESERVICE("openrouteservice"),
    COMPOSITE("composite");

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProviderType fromString(String text) {
        if (text == null || text.isBlank()) {
            return OPENSTREETMAP;
        }
        String normalized = text.trim().toLowerCase();
        for (ProviderType type : values()) {
            if (type.value.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        if ("osm".equals(normalized)) {
            return OPENSTREETMAP;
        }
        if ("ors".equals(normalized)) {
            return OPENROUTESERVICE;
        }
        throw new IllegalArgumentException("Unknown map provider: '" + text + "'");
    }
}
