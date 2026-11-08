package com.example.mapsmcp.dto.response;

import com.example.mapsmcp.models.DistanceMatrixElement;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DistanceMatrixResponse(
        List<String> originAddresses,
        List<String> destinationAddresses,
        String travelMode,
        List<DistanceMatrixElement> elements,
        String provider
) {
}
