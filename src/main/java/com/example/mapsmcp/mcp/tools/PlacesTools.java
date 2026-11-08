package com.example.mapsmcp.mcp.tools;

import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.services.PlacesService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PlacesTools {

    private final PlacesService placesService;
    private final McpExecutionHandler executionHandler;

    public PlacesTools(PlacesService placesService, McpExecutionHandler executionHandler) {
        this.placesService = placesService;
        this.executionHandler = executionHandler;
    }

    @Tool(name = "search_places", description = "Search for locations, addresses, landmarks, cities, and businesses by text query, with optional GPS location bias, radius, category, and rating filters.")
    public McpToolResponse<List<PlaceSummary>> searchPlaces(
            @ToolParam(description = "Text search query (e.g., 'restaurants in Kolkata', 'Victoria Memorial', 'coffee shops')", required = true) String query,
            @ToolParam(description = "Optional latitude (-90 to 90) to bias or center the search", required = false) Double latitude,
            @ToolParam(description = "Optional longitude (-180 to 180) to bias or center the search", required = false) Double longitude,
            @ToolParam(description = "Optional search radius in meters (1 to 50000, default 5000)", required = false) Integer radiusMeters,
            @ToolParam(description = "Optional category filter (e.g., restaurant, hotel, hospital, atm, petrol_pump, cafe, shopping_mall)", required = false) String category,
            @ToolParam(description = "Optional filter to return only businesses currently open (when supported by provider)", required = false) Boolean openNow,
            @ToolParam(description = "Optional minimum rating filter (0.0 to 5.0)", required = false) Double minRating,
            @ToolParam(description = "Optional maximum number of results to return (1 to 50, default 10)", required = false) Integer limit
    ) {
        return executionHandler.execute("search_places", placesService.getActiveProviderName(), () ->
                placesService.searchPlaces(new SearchPlacesRequest(
                        query, latitude, longitude, radiusMeters, category, openNow, minRating, limit
                ))
        );
    }

    @Tool(name = "search_nearby", description = "Search nearby points of interest and businesses around GPS coordinates within a given radius in meters, sorted by distance.")
    public McpToolResponse<List<PlaceSummary>> searchNearby(
            @ToolParam(description = "Center latitude (-90.0 to 90.0)", required = true) Double latitude,
            @ToolParam(description = "Center longitude (-180.0 to 180.0)", required = true) Double longitude,
            @ToolParam(description = "Search radius in meters (1 to 50000, default 5000)", required = false) Integer radiusMeters,
            @ToolParam(description = "Category of place (e.g., restaurant, hospital, hotel, atm, petrol_pump, pharmacy, cafe)", required = false) String category,
            @ToolParam(description = "Optional keyword to match business name or type", required = false) String keyword,
            @ToolParam(description = "Optional filter for currently open businesses", required = false) Boolean openNow,
            @ToolParam(description = "Optional minimum star rating (0.0 to 5.0)", required = false) Double minRating,
            @ToolParam(description = "Maximum number of results to return (1 to 50, default 10)", required = false) Integer limit
    ) {
        return executionHandler.execute("search_nearby", placesService.getActiveProviderName(), () ->
                placesService.searchNearby(new SearchNearbyRequest(
                        latitude, longitude, radiusMeters, category, keyword, openNow, minRating, limit
                ))
        );
    }

    @Tool(name = "get_place_details", description = "Retrieve comprehensive details for a specific place or business by its placeId, including address, coordinates, phone number, website, opening hours, photos, and reviews where available.")
    public McpToolResponse<PlaceDetails> getPlaceDetails(
            @ToolParam(description = "Unique place identifier (e.g., Google place_id or OpenStreetMap 'osm:node:123456')", required = true) String placeId
    ) {
        return executionHandler.execute("get_place_details", placesService.getActiveProviderName(), () ->
                placesService.getPlaceDetails(placeId)
        );
    }

    @Tool(name = "autocomplete_location", description = "Provide location, address, and place autocomplete suggestions for partial text input, optionally biased around GPS coordinates.")
    public McpToolResponse<List<AutocompleteSuggestion>> autocompleteLocation(
            @ToolParam(description = "Partial location or address text to autocomplete", required = true) String input,
            @ToolParam(description = "Optional bias latitude (-90.0 to 90.0)", required = false) Double latitude,
            @ToolParam(description = "Optional bias longitude (-180.0 to 180.0)", required = false) Double longitude,
            @ToolParam(description = "Optional bias radius in meters (1 to 50000)", required = false) Integer radiusMeters,
            @ToolParam(description = "Maximum number of suggestions to return (1 to 50, default 10)", required = false) Integer limit
    ) {
        return executionHandler.execute("autocomplete_location", placesService.getActiveProviderName(), () ->
                placesService.autocompleteLocation(input, latitude, longitude, radiusMeters, limit)
        );
    }

    @Tool(name = "get_place_photos", description = "Retrieve verified photo metadata, dimensions, attributions, and image URLs for a specific placeId where permitted by the provider.")
    public McpToolResponse<List<PlacePhoto>> getPlacePhotos(
            @ToolParam(description = "Unique place identifier", required = true) String placeId,
            @ToolParam(description = "Optional maximum image width in pixels (default 800)", required = false) Integer maxWidth
    ) {
        return executionHandler.execute("get_place_photos", placesService.getActiveProviderName(), () ->
                placesService.getPlacePhotos(placeId, maxWidth)
        );
    }

    @Tool(name = "get_place_reviews", description = "Retrieve authentic user reviews and ratings for a placeId when supported by the configured map provider. Never fabricates reviews.")
    public McpToolResponse<List<PlaceReview>> getPlaceReviews(
            @ToolParam(description = "Unique place identifier", required = true) String placeId
    ) {
        return executionHandler.execute("get_place_reviews", placesService.getActiveProviderName(), () ->
                placesService.getPlaceReviews(placeId)
        );
    }

    @Tool(name = "get_place_opening_hours", description = "Retrieve opening hours schedule and current open/closed status for a specific placeId when reliable opening-hours data exists.")
    public McpToolResponse<OpeningHoursInfo> getPlaceOpeningHours(
            @ToolParam(description = "Unique place identifier", required = true) String placeId
    ) {
        return executionHandler.execute("get_place_opening_hours", placesService.getActiveProviderName(), () ->
                placesService.getPlaceOpeningHours(placeId)
        );
    }

    @Tool(name = "find_nearby_categories", description = "Search multiple place categories (e.g., restaurants, hospitals, ATMs, petrol pumps, hotels) simultaneously around GPS coordinates.")
    public McpToolResponse<Map<String, List<PlaceSummary>>> findNearbyCategories(
            @ToolParam(description = "Center latitude (-90.0 to 90.0)", required = true) Double latitude,
            @ToolParam(description = "Center longitude (-180.0 to 180.0)", required = true) Double longitude,
            @ToolParam(description = "List of categories to search (e.g., ['restaurant', 'hospital', 'atm', 'petrol_pump'])", required = true) List<String> categories,
            @ToolParam(description = "Search radius in meters (1 to 50000, default 5000)", required = false) Integer radiusMeters,
            @ToolParam(description = "Maximum results per category (1 to 50, default 5)", required = false) Integer limitPerCategory
    ) {
        return executionHandler.execute("find_nearby_categories", placesService.getActiveProviderName(), () ->
                placesService.findNearbyCategories(latitude, longitude, categories, radiusMeters, limitPerCategory)
        );
    }
}
