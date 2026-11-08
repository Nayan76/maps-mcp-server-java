package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.services.DirectionsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DirectionsTools {

    private final DirectionsService directionsService;
    private final McpExecutionHandler executionHandler;

    public DirectionsTools(DirectionsService directionsService, McpExecutionHandler executionHandler) {
        this.directionsService = directionsService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "get_directions", description = "Get turn-by-turn navigation directions, total distance, estimated travel duration, and route geometry between an origin and destination, with optional intermediate waypoints and travel mode (driving, walking, cycling, transit).")
    public McpToolResponse<DirectionsResponse> getDirections(
            @ToolParam(description = "Starting location (address, landmark name, or 'latitude,longitude')", required = true) String origin,
            @ToolParam(description = "Destination location (address, landmark name, or 'latitude,longitude')", required = true) String destination,
            @ToolParam(description = "Transportation mode: driving, walking, cycling, or transit (default: driving)", required = false) String travelMode,
            @ToolParam(description = "Optional list of intermediate waypoints or stops along the route", required = false) List<String> waypoints,
            @ToolParam(description = "Optional flag to request alternative routes if available (default: false)", required = false) Boolean alternatives,
            @ToolParam(description = "Optional flag to avoid toll roads where supported (default: false)", required = false) Boolean avoidTolls,
            @ToolParam(description = "Optional flag to avoid highways/motorways where supported (default: false)", required = false) Boolean avoidHighways,
            @ToolParam(description = "Optional flag to avoid ferries where supported (default: false)", required = false) Boolean avoidFerries
    ) {
        return executionHandler.execute("get_directions", directionsService.getActiveProviderName(), () -> {
            TravelMode mode = TravelMode.fromString(travelMode);
            DirectionsRequest req = new DirectionsRequest(
                    origin,
                    destination,
                    mode,
                    waypoints != null ? waypoints : List.of(),
                    Boolean.TRUE.equals(alternatives),
                    Boolean.TRUE.equals(avoidTolls),
                    Boolean.TRUE.equals(avoidHighways),
                    Boolean.TRUE.equals(avoidFerries)
            );
            return directionsService.getDirections(req);
        });
    }

    @Tool(name = "get_route_alternatives", description = "Compute and compare primary and alternative routes between an origin and destination, returning route summaries, distances, travel times, and turn-by-turn steps for each option.")
    public McpToolResponse<DirectionsResponse> getRouteAlternatives(
            @ToolParam(description = "Starting location (address, landmark, or 'latitude,longitude')", required = true) String origin,
            @ToolParam(description = "Destination location (address, landmark, or 'latitude,longitude')", required = true) String destination,
            @ToolParam(description = "Transportation mode: driving, walking, cycling, or transit (default: driving)", required = false) String travelMode
    ) {
        return executionHandler.execute("get_route_alternatives", directionsService.getActiveProviderName(), () ->
                directionsService.getRouteAlternatives(origin, destination, travelMode)
        );
    }
}
