package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceSummary(
        String placeId,
        String name,
        String formattedAddress,
        String postalCode,
        Coordinates coordinates,
        List<String> categories,
        Double rating,
        Integer userRatingsTotal,
        Integer priceLevel,
        Boolean openNow,
        Double distanceMeters,
        String businessStatus,
        String phoneNumber,
        String website,
        String provider,
        Map<String, String> additionalTags
) {
}
