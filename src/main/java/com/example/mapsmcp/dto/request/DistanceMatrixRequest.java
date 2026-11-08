package com.example.mapsmcp.dto.request;

import com.example.mapsmcp.enums.TravelMode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DistanceMatrixRequest(
        @NotEmpty(message = "At least one origin is required")
        List<String> origins,
        @NotEmpty(message = "At least one destination is required")
        List<String> destinations,
        TravelMode travelMode
) {
}
