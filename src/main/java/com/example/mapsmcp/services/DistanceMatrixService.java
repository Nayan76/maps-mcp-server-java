package com.example.mapsmcp.services;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.providers.DistanceMatrixProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DistanceMatrixService {

    private static final int MAX_MATRIX_ELEMENTS = 100;

    private final ProviderSelector providerSelector;

    public DistanceMatrixService(ProviderSelector providerSelector) {
        this.providerSelector = providerSelector;
    }

    public String getActiveProviderName() {
        return providerSelector.selectDistanceMatrixProvider().getProviderName();
    }

    @Cacheable(value = MapConstants.CACHE_DISTANCE_MATRIX, key = "#request.toString()")
    public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
        if (request.origins() == null || request.origins().isEmpty()) {
            throw new ValidationException("At least one origin must be provided.");
        }
        if (request.destinations() == null || request.destinations().isEmpty()) {
            throw new ValidationException("At least one destination must be provided.");
        }
        int totalElements = request.origins().size() * request.destinations().size();
        if (totalElements > MAX_MATRIX_ELEMENTS) {
            throw new ValidationException(
                    "Distance matrix size (" + totalElements + " elements) exceeds provider maximum limit of "
                            + MAX_MATRIX_ELEMENTS + " elements (origins * destinations <= 100)."
            );
        }

        DistanceMatrixProvider provider = providerSelector.selectDistanceMatrixProvider();
        return provider.getDistanceMatrix(request);
    }
}
