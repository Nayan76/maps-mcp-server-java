package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Coordinates(
        double latitude,
        double longitude
) {
    public String toLatLngString() {
        return latitude + "," + longitude;
    }

    public String toLngLatString() {
        return longitude + "," + latitude;
    }
}
