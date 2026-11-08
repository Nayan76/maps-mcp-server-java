package com.example.mapsmcp.dto.response;

import com.example.mapsmcp.models.RouteOption;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DirectionsResponse(
        String originQuery,
        String destinationQuery,
        String travelMode,
        int routeCount,
        RouteOption primaryRoute,
        List<RouteOption> alternativeRoutes,
        String provider
) {
}
