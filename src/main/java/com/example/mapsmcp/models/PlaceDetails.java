package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceDetails(
        String placeId,
        String name,
        String formattedAddress,
        String postalCode,
        Coordinates coordinates,
        BoundingBox viewport,
        List<String> types,
        String phoneNumber,
        String internationalPhoneNumber,
        String website,
        String googleMapsUrl,
        Double rating,
        Integer userRatingsTotal,
        Integer priceLevel,
        String businessStatus,
        OpeningHoursInfo openingHours,
        List<PlacePhoto> photos,
        List<PlaceReview> reviews,
        Map<String, String> additionalAttributes,
        String provider
) {
}
