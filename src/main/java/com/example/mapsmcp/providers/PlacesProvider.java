package com.example.mapsmcp.providers;

import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;

import java.util.List;

public interface PlacesProvider extends MapProvider {

    List<PlaceSummary> searchPlaces(SearchPlacesRequest request);

    List<PlaceSummary> searchNearby(SearchNearbyRequest request);

    PlaceDetails getPlaceDetails(String placeId);

    List<AutocompleteSuggestion> autocompleteLocation(String input, Coordinates biasLocation, Integer radiusMeters, int limit);

    List<PlacePhoto> getPlacePhotos(String placeId, Integer maxWidth);

    List<PlaceReview> getPlaceReviews(String placeId);

    OpeningHoursInfo getPlaceOpeningHours(String placeId);
}
