package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpeningHoursInfo(
        String placeId,
        String placeName,
        Boolean openNow,
        List<String> weekdayText,
        String rawOpeningHours,
        Boolean dataAvailable,
        String message,
        String provider
) {
}
