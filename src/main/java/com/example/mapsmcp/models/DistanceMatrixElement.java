package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DistanceMatrixElement(
        int originIndex,
        int destinationIndex,
        String originAddress,
        String destinationAddress,
        String status,
        Double distanceMeters,
        String distanceText,
        Long durationSeconds,
        String durationText,
        Long durationInTrafficSeconds
) {
}
