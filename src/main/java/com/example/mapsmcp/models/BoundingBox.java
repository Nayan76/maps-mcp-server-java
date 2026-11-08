package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoundingBox(
        double southLat,
        double westLon,
        double northLat,
        double eastLon
) {
    public Coordinates center() {
        return new Coordinates((southLat + northLat) / 2.0, (westLon + eastLon) / 2.0);
    }
}
