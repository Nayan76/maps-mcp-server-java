package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteStep(
        int stepNumber,
        String instruction,
        double distanceMeters,
        String distanceText,
        long durationSeconds,
        String durationText,
        Coordinates startLocation,
        Coordinates endLocation,
        String maneuver,
        String roadName,
        String travelMode
) {
}
