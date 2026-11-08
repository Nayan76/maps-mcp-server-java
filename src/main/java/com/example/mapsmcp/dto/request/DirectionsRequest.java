package com.example.mapsmcp.dto.request;

import com.example.mapsmcp.enums.TravelMode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DirectionsRequest(
        @NotBlank(message = "Origin is required (address or 'lat,lon')")
        String origin,
        @NotBlank(message = "Destination is required (address or 'lat,lon')")
        String destination,
        TravelMode travelMode,
        List<String> waypoints,
        boolean alternatives,
        boolean avoidTolls,
        boolean avoidHighways,
        boolean avoidFerries
) {
}
