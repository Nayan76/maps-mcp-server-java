package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.models.AreaCalculationResult;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.services.GeocodingService;
import com.example.mapsmcp.services.GeographyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeographyTools {

    private final GeographyService geographyService;
    private final GeocodingService geocodingService;
    private final McpExecutionHandler executionHandler;

    public GeographyTools(GeographyService geographyService,
                          GeocodingService geocodingService,
                          McpExecutionHandler executionHandler) {
        this.geographyService = geographyService;
        this.geocodingService = geocodingService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "get_timezone", description = "Retrieve the IANA timezone ID, timezone display name, UTC offset, DST offset, and current local ISO time for given GPS coordinates.")
    public McpToolResponse<TimezoneInfo> getTimezone(
            @ToolParam(description = "Latitude in decimal degrees (-90.0 to 90.0)", required = true) Double latitude,
            @ToolParam(description = "Longitude in decimal degrees (-180.0 to 180.0)", required = true) Double longitude,
            @ToolParam(description = "Optional Unix epoch timestamp in seconds (defaults to current time)", required = false) Long timestampSeconds
    ) {
        return executionHandler.execute("get_timezone", geographyService.getActiveElevationProviderName(), () ->
                geographyService.getTimezone(latitude, longitude, timestampSeconds)
        );
    }

    @Tool(name = "get_elevation", description = "Retrieve terrain elevation in meters above sea level for one or more GPS coordinate points.")
    public McpToolResponse<List<ElevationPoint>> getElevation(
            @ToolParam(description = "List of GPS coordinates (latitude and longitude) to query elevation for", required = true) List<Coordinates> locations
    ) {
        return executionHandler.execute("get_elevation", geographyService.getActiveElevationProviderName(), () ->
                geographyService.getElevation(locations)
        );
    }

    @Tool(name = "calculate_area", description = "Calculate the geodesic area (in square meters, square kilometers, hectares, and acres), perimeter, centroid, bounding box, and GeoJSON geometry of a geographical polygon defined by at least 3 GPS coordinate vertices using JTS Topology Suite.")
    public McpToolResponse<AreaCalculationResult> calculateArea(
            @ToolParam(description = "Ordered list of at least 3 polygon vertex coordinates (latitude and longitude)", required = true) List<Coordinates> vertices
    ) {
        return executionHandler.execute("calculate_area", "JTS Geodesic GIS Engine", () ->
                geographyService.calculateArea(vertices)
        );
    }

    @Tool(name = "get_country_and_city_info", description = "Retrieve geographical, administrative, bounding box, boundary GeoJSON, population, Wikidata/Wikipedia references, and address metadata for a city, state, or country.")
    public McpToolResponse<CountryCityInfo> getCountryAndCityInfo(
            @ToolParam(description = "Name of the city, state/region, or country (e.g., 'Kolkata', 'Tokyo', 'India')", required = true) String query,
            @ToolParam(description = "Optional 2-letter ISO country code to disambiguate cities (e.g., 'IN', 'US')", required = false) String countryCode
    ) {
        return executionHandler.execute("get_country_and_city_info", geocodingService.getActiveProviderName(), () ->
                geocodingService.getCountryAndCityInfo(query, countryCode)
        );
    }
}
