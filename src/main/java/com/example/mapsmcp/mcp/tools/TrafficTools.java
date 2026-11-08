package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.services.TrafficService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TrafficTools {

    private final TrafficService trafficService;
    private final MapsProperties mapsProperties;
    private final McpExecutionHandler executionHandler;

    public TrafficTools(TrafficService trafficService,
                        MapsProperties mapsProperties,
                        McpExecutionHandler executionHandler) {
        this.trafficService = trafficService;
        this.mapsProperties = mapsProperties;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "get_traffic_conditions", description = "Retrieve live traffic congestion, travel time delays, and road warnings along a route or around GPS coordinates when supported by the configured map provider. Never fabricates live traffic data.")
    public McpToolResponse<TrafficConditionsResponse> getTrafficConditions(
            @ToolParam(description = "Optional center latitude (-90.0 to 90.0) for area traffic check", required = false) Double latitude,
            @ToolParam(description = "Optional center longitude (-180.0 to 180.0) for area traffic check", required = false) Double longitude,
            @ToolParam(description = "Optional radius in meters around the coordinates (default 3000)", required = false) Integer radiusMeters,
            @ToolParam(description = "Optional route origin (address or 'lat,lon') for corridor traffic check", required = false) String origin,
            @ToolParam(description = "Optional route destination (address or 'lat,lon') for corridor traffic check", required = false) String destination
    ) {
        return executionHandler.execute("get_traffic_conditions", mapsProperties.getPrimaryProviderType().getValue(), () ->
                trafficService.getTrafficConditions(latitude, longitude, radiusMeters, origin, destination)
        );
    }
}
