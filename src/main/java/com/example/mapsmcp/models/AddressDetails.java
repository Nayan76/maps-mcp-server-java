package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressDetails(
        String formattedAddress,
        String streetNumber,
        String street,
        String neighborhood,
        String city,
        String district,
        String state,
        String country,
        String countryCode,
        String postalCode
) {
}
