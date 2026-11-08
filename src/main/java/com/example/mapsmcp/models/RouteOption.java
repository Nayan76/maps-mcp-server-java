package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteOption(
        int routeIndex,
        String summary,
        double totalDistanceMeters,
        String totalDistanceText,
        long totalDurationSeconds,
        String totalDurationText,
        Long durationInTrafficSeconds,
        String durationInTrafficText,
        Coordinates startLocation,
        String startAddress,
        Coordinates endLocation,
        String endAddress,
        BoundingBox bounds,
        String overviewPolyline,
        List<Coordinates> geometryCoordinates,
        List<RouteStep> steps,
        List<String> warnings,
        Boolean hasTolls,
        List<String> roadRestrictions
) {
}
