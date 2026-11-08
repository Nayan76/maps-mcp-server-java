package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElevationPoint(
        Coordinates coordinates,
        double elevationMeters,
        Double resolutionMeters,
        String provider
) {
}
