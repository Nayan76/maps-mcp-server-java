package com.example.mapsmcp.services;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.providers.PlacesProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.validation.GeoValidator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlacesService {

    private final ProviderSelector providerSelector;
    private final GeoValidator geoValidator;

    public PlacesService(ProviderSelector providerSelector, GeoValidator geoValidator) {
        this.providerSelector = providerSelector;
        this.geoValidator = geoValidator;
    }

    public String getActiveProviderName() {
        return providerSelector.selectPlacesProvider().getProviderName();
    }

    @Cacheable(value = MapConstants.CACHE_PLACES, key = "'search:' + #request.toString()")
    public List<PlaceSummary> searchPlaces(SearchPlacesRequest request) {
        geoValidator.validateNotBlank(request.query(), "query");
        if (request.latitude() != null || request.longitude() != null) {
            geoValidator.validateCoordinates(request.latitude(), request.longitude());
        }
        int radius = geoValidator.validateAndNormalizeRadius(request.radiusMeters());
        int limit = geoValidator.validateAndNormalizeLimit(request.limit());

        SearchPlacesRequest normalized = new SearchPlacesRequest(
                request.query().trim(),
                request.latitude(),
                request.longitude(),
                radius,
                request.category(),
                request.openNow(),
                request.minRating(),
                limit
        );
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.searchPlaces(normalized);
    }

    @Cacheable(value = MapConstants.CACHE_PLACES, key = "'nearby:' + #request.toString()")
    public List<PlaceSummary> searchNearby(SearchNearbyRequest request) {
        geoValidator.validateCoordinates(request.latitude(), request.longitude());
        int radius = geoValidator.validateAndNormalizeRadius(request.radiusMeters());
        int limit = geoValidator.validateAndNormalizeLimit(request.limit());

        SearchNearbyRequest normalized = new SearchNearbyRequest(
                request.latitude(),
                request.longitude(),
                radius,
                request.category(),
                request.keyword(),
                request.openNow(),
                request.minRating(),
                limit
        );
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.searchNearby(normalized);
    }

    @Cacheable(value = MapConstants.CACHE_PLACES, key = "'details:' + #placeId")
    public PlaceDetails getPlaceDetails(String placeId) {
        geoValidator.validateNotBlank(placeId, "placeId");
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.getPlaceDetails(placeId.trim());
    }

    @Cacheable(value = MapConstants.CACHE_PLACES, key = "'autocomplete:' + #input + ':' + #latitude + ':' + #longitude + ':' + #radiusMeters + ':' + #limit")
    public List<AutocompleteSuggestion> autocompleteLocation(String input, Double latitude, Double longitude, Integer radiusMeters, Integer limit) {
        geoValidator.validateNotBlank(input, "input");
        Coordinates bias = null;
        if (latitude != null || longitude != null) {
            geoValidator.validateCoordinates(latitude, longitude);
            bias = new Coordinates(latitude, longitude);
        }
        int radius = geoValidator.validateAndNormalizeRadius(radiusMeters);
        int normLimit = geoValidator.validateAndNormalizeLimit(limit);

        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.autocompleteLocation(input.trim(), bias, radius, normLimit);
    }

    public List<PlacePhoto> getPlacePhotos(String placeId, Integer maxWidth) {
        geoValidator.validateNotBlank(placeId, "placeId");
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.getPlacePhotos(placeId.trim(), maxWidth != null ? maxWidth : 800);
    }

    public List<PlaceReview> getPlaceReviews(String placeId) {
        geoValidator.validateNotBlank(placeId, "placeId");
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.getPlaceReviews(placeId.trim());
    }

    public OpeningHoursInfo getPlaceOpeningHours(String placeId) {
        geoValidator.validateNotBlank(placeId, "placeId");
        PlacesProvider provider = providerSelector.selectPlacesProvider();
        return provider.getPlaceOpeningHours(placeId.trim());
    }

    public Map<String, List<PlaceSummary>> findNearbyCategories(Double latitude, Double longitude, List<String> categories, Integer radiusMeters, Integer limitPerCategory) {
        geoValidator.validateCoordinates(latitude, longitude);
        if (categories == null || categories.isEmpty()) {
            categories = List.of("restaurant", "hospital", "atm", "petrol_pump", "hotel");
        }
        int radius = geoValidator.validateAndNormalizeRadius(radiusMeters);
        int limit = geoValidator.validateAndNormalizeLimit(limitPerCategory != null ? limitPerCategory : 5);

        PlacesProvider provider = providerSelector.selectPlacesProvider();
        Map<String, List<PlaceSummary>> grouped = new LinkedHashMap<>();
        for (String cat : categories) {
            if (cat != null && !cat.isBlank()) {
                SearchNearbyRequest req = new SearchNearbyRequest(
                        latitude, longitude, radius, cat.trim(), null, null, null, limit
                );
                grouped.put(cat.trim(), provider.searchNearby(req));
            }
        }
        return grouped;
    }
}
