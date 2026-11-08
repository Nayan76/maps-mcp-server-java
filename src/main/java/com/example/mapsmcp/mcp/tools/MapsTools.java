package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.services.GeographyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MapsTools {

    private final GeographyService geographyService;
    private final McpExecutionHandler executionHandler;

    public MapsTools(GeographyService geographyService, McpExecutionHandler executionHandler) {
        this.geographyService = geographyService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "get_map_data", description = "Retrieve map tile URL, slippy map tile coordinates (x, y, z), tile bounding box, viewport bounding box, and GeoJSON polygon representation for given GPS coordinates and zoom level.")
    public McpToolResponse<MapTileData> getMapData(
            @ToolParam(description = "Center latitude (-90.0 to 90.0)", required = true) Double latitude,
            @ToolParam(description = "Center longitude (-180.0 to 180.0)", required = true) Double longitude,
            @ToolParam(description = "Map zoom level (0 for world view to 19 for street/building level, default 14)", required = false) Integer zoomLevel
    ) {
        return executionHandler.execute("get_map_data", geographyService.getActiveTileProviderName(), () ->
                geographyService.getMapData(latitude, longitude, zoomLevel)
        );
    }
}
