package com.example.mapsmcp.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchPlacesRequest(
        @NotBlank(message = "Search query must not be blank")
        String query,
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
        Double latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
        Double longitude,
        @Min(value = 1, message = "Radius must be at least 1 meter")
        @Max(value = 50000, message = "Radius cannot exceed 50,000 meters")
        Integer radiusMeters,
        String category,
        Boolean openNow,
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "5.0")
        Double minRating,
        @Min(value = 1)
        @Max(value = 50)
        Integer limit
) {
}
