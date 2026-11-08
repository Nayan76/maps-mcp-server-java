package com.example.mapsmcp.dto.response;

import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.TrafficIncident;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrafficConditionsResponse(
        boolean liveTrafficSupported,
        String locationOrRoute,
        Coordinates coordinates,
        String congestionLevel,
        Long normalDurationSeconds,
        Long durationInTrafficSeconds,
        Double delayRatio,
        List<TrafficIncident> incidents,
        String provider,
        String limitationNote
) {
}
