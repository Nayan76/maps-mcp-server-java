package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapTileData(
        Coordinates center,
        int zoomLevel,
        int tileX,
        int tileY,
        String tileUrl,
        String tileUrlTemplate,
        BoundingBox tileBoundingBox,
        BoundingBox viewportBoundingBox,
        Map<String, Object> geoJsonPolygon,
        String attribution,
        String usagePolicyNote,
        String provider
) {
}
