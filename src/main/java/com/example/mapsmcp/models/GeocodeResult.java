package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeocodeResult(
        String placeId,
        String formattedAddress,
        Coordinates coordinates,
        AddressDetails addressDetails,
        BoundingBox boundingBox,
        String locationType,
        String category,
        String provider
) {
}
