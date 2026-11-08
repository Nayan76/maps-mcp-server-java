package com.example.mapsmcp.services;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.providers.DirectionsProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.validation.GeoValidator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectionsService {

    private final ProviderSelector providerSelector;
    private final GeoValidator geoValidator;

    public DirectionsService(ProviderSelector providerSelector, GeoValidator geoValidator) {
        this.providerSelector = providerSelector;
        this.geoValidator = geoValidator;
    }

    public String getActiveProviderName() {
        return providerSelector.selectDirectionsProvider().getProviderName();
    }

    @Cacheable(value = MapConstants.CACHE_DIRECTIONS, key = "#request.toString()")
    public DirectionsResponse getDirections(DirectionsRequest request) {
        geoValidator.validateNotBlank(request.origin(), "origin");
        geoValidator.validateNotBlank(request.destination(), "destination");

        DirectionsProvider provider = providerSelector.selectDirectionsProvider();
        return provider.getDirections(request);
    }

    @Cacheable(value = MapConstants.CACHE_DIRECTIONS, key = "'alt:' + #origin + ':' + #destination + ':' + #travelMode")
    public DirectionsResponse getRouteAlternatives(String origin, String destination, String travelMode) {
        geoValidator.validateNotBlank(origin, "origin");
        geoValidator.validateNotBlank(destination, "destination");
        TravelMode mode = TravelMode.fromString(travelMode);

        DirectionsRequest request = new DirectionsRequest(
                origin.trim(),
                destination.trim(),
                mode,
                List.of(),
                true,
                false,
                false,
                false
        );
        DirectionsProvider provider = providerSelector.selectDirectionsProvider();
        return provider.getDirections(request);
    }
}
