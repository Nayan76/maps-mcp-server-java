package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutocompleteSuggestion(
        String placeId,
        String description,
        String mainText,
        String secondaryText,
        List<String> types,
        Coordinates coordinates,
        String provider
) {
}
