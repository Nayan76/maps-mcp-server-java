package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimezoneInfo(
        Coordinates coordinates,
        String timeZoneId,
        String timeZoneName,
        int rawOffsetSeconds,
        int dstOffsetSeconds,
        String localTimeIso,
        String provider
) {
}
