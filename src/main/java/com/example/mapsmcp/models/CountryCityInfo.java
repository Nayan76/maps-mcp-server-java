package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CountryCityInfo(
        String query,
        String displayName,
        String placeType,
        String city,
        String state,
        String country,
        String countryCode,
        Coordinates coordinates,
        BoundingBox boundingBox,
        String timezoneId,
        Long population,
        String wikidataId,
        String wikipediaRef,
        Map<String, Object> geoJsonBoundary,
        Map<String, String> extraMetadata,
        String provider
) {
}
