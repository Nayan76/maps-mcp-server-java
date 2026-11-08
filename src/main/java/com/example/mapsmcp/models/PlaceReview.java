package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceReview(
        String authorName,
        String authorUrl,
        Double rating,
        String relativeTimeDescription,
        String text,
        Long timeEpochSeconds,
        String language
) {
}
