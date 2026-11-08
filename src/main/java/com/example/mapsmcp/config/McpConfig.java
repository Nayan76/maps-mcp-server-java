package com.example.mapsmcp.config;

import com.example.mapsmcp.mcp.tools.DirectionsTools;
import com.example.mapsmcp.mcp.tools.DistanceTools;
import com.example.mapsmcp.mcp.tools.GeocodingTools;
import com.example.mapsmcp.mcp.tools.GeographyTools;
import com.example.mapsmcp.mcp.tools.MapsTools;
import com.example.mapsmcp.mcp.tools.PlacesTools;
import com.example.mapsmcp.mcp.tools.TrafficTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider mapsMcpToolCallbackProvider(
            PlacesTools placesTools,
            DirectionsTools directionsTools,
            DistanceTools distanceTools,
            GeocodingTools geocodingTools,
            MapsTools mapsTools,
            TrafficTools trafficTools,
            GeographyTools geographyTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        placesTools,
                        directionsTools,
                        distanceTools,
                        geocodingTools,
                        mapsTools,
                        trafficTools,
                        geographyTools
                )
                .build();
    }
}
