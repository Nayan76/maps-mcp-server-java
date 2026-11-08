package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.services.GeocodingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeocodingTools {

    private final GeocodingService geocodingService;
    private final McpExecutionHandler executionHandler;

    public GeocodingTools(GeocodingService geocodingService, McpExecutionHandler executionHandler) {
        this.geocodingService = geocodingService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "geocode_address", description = "Forward geocode a street address, postal code, landmark, or place name into GPS coordinates (latitude and longitude), formatted address, postal code, city, state, country, and bounding box.")
    public McpToolResponse<List<GeocodeResult>> geocodeAddress(
            @ToolParam(description = "Street address, postal code, or place name to geocode", required = true) String address,
            @ToolParam(description = "Optional ISO 3166-1 alpha-2 country code filter (e.g., 'IN', 'US', 'GB')", required = false) String countryCode,
            @ToolParam(description = "Optional maximum number of matching results (1 to 20, default 5)", required = false) Integer limit
    ) {
        return executionHandler.execute("geocode_address", geocodingService.getActiveProviderName(), () ->
                geocodingService.geocodeAddress(address, countryCode, limit)
        );
    }

    @Tool(name = "reverse_geocode", description = "Reverse geocode GPS coordinates (latitude and longitude) into a human-readable street address, postal code, neighborhood, city, state, and country.")
    public McpToolResponse<GeocodeResult> reverseGeocode(
            @ToolParam(description = "Latitude in decimal degrees (-90.0 to 90.0)", required = true) Double latitude,
            @ToolParam(description = "Longitude in decimal degrees (-180.0 to 180.0)", required = true) Double longitude
    ) {
        return executionHandler.execute("reverse_geocode", geocodingService.getActiveProviderName(), () ->
                geocodingService.reverseGeocode(latitude, longitude)
        );
    }

    @Tool(name = "get_location_coordinates", description = "Convert a single named location, landmark, city, or address directly into its primary GPS coordinates (latitude and longitude) and structured address.")
    public McpToolResponse<GeocodeResult> getLocationCoordinates(
            @ToolParam(description = "Name of the location, landmark, city, or address", required = true) String locationName,
            @ToolParam(description = "Optional ISO 2-letter country code (e.g., 'IN', 'US')", required = false) String countryCode
    ) {
        return executionHandler.execute("get_location_coordinates", geocodingService.getActiveProviderName(), () ->
                geocodingService.getLocationCoordinates(locationName, countryCode)
        );
    }
}
