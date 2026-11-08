package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AreaCalculationResult(
        double areaSquareMeters,
        double areaSquareKilometers,
        double areaHectares,
        double areaAcres,
        double perimeterMeters,
        double perimeterKilometers,
        Coordinates centroid,
        BoundingBox boundingBox,
        int vertexCount,
        boolean validGeometry,
        Map<String, Object> geoJsonPolygon,
        List<Coordinates> closedVertices
) {
}
