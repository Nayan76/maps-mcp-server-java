package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.services.DistanceMatrixService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DistanceTools {

    private final DistanceMatrixService distanceMatrixService;
    private final McpExecutionHandler executionHandler;

    public DistanceTools(DistanceMatrixService distanceMatrixService, McpExecutionHandler executionHandler) {
        this.distanceMatrixService = distanceMatrixService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "get_distance_matrix", description = "Calculate travel distances and estimated travel durations between multiple origins and multiple destinations for a specified transportation mode (driving, walking, cycling, transit).")
    public McpToolResponse<DistanceMatrixResponse> getDistanceMatrix(
            @ToolParam(description = "List of origin addresses, city names, or 'latitude,longitude' coordinates", required = true) List<String> origins,
            @ToolParam(description = "List of destination addresses, city names, or 'latitude,longitude' coordinates", required = true) List<String> destinations,
            @ToolParam(description = "Transportation mode: driving, walking, cycling, or transit (default: driving)", required = false) String travelMode
    ) {
        return executionHandler.execute("get_distance_matrix", distanceMatrixService.getActiveProviderName(), () -> {
            TravelMode mode = TravelMode.fromString(travelMode);
            return distanceMatrixService.getDistanceMatrix(new DistanceMatrixRequest(origins, destinations, mode));
        });
    }
}
