package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlacePhoto(
        String photoReference,
        Integer width,
        Integer height,
        String photoUrl,
        List<String> htmlAttributions,
        String provider
) {
}
