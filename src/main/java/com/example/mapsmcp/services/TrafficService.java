package com.example.mapsmcp.services;

import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.providers.TrafficProvider;
import com.example.mapsmcp.validation.GeoValidator;
import org.springframework.stereotype.Service;

@Service
public class TrafficService {

    private final ProviderSelector providerSelector;
    private final GeoValidator geoValidator;

    public TrafficService(ProviderSelector providerSelector, GeoValidator geoValidator) {
        this.providerSelector = providerSelector;
        this.geoValidator = geoValidator;
    }

    public TrafficConditionsResponse getTrafficConditions(Double latitude, Double longitude, Integer radiusMeters, String origin, String destination) {
        Coordinates center = null;
        if (latitude != null || longitude != null) {
            geoValidator.validateCoordinates(latitude, longitude);
            center = new Coordinates(latitude, longitude);
        }
        boolean hasRoute = origin != null && !origin.isBlank() && destination != null && !destination.isBlank();
        if (center == null && !hasRoute) {
            throw new ValidationException(
                    "Either (latitude, longitude) or both (origin, destination) must be provided to check traffic conditions."
            );
        }
        Integer normRadius = radiusMeters != null ? geoValidator.validateAndNormalizeRadius(radiusMeters) : 3000;
        TrafficProvider provider = providerSelector.selectTrafficProvider();
        return provider.getTrafficConditions(center, normRadius, origin, destination);
    }
}
