package com.example.mapsmcp.utils;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.models.AreaCalculationResult;
import com.example.mapsmcp.models.BoundingBox;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.MapTileData;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GeoMathUtils {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoMathUtils() {
    }

    /**
     * Calculates the great-circle distance in meters between two GPS coordinates using the Haversine formula.
     */
    public static double haversineDistanceMeters(Coordinates c1, Coordinates c2) {
        return haversineDistanceMeters(c1.latitude(), c1.longitude(), c2.latitude(), c2.longitude());
    }

    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(MapConstants.EARTH_RADIUS_METERS * c * 100.0) / 100.0;
    }

    /**
     * Computes a bounding box around a center point for a given radius in meters.
     */
    public static BoundingBox boundingBoxFromRadius(double lat, double lon, double radiusMeters) {
        double latDelta = Math.toDegrees(radiusMeters / MapConstants.EARTH_RADIUS_METERS);
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.0001);
        double lonDelta = Math.toDegrees(radiusMeters / (MapConstants.EARTH_RADIUS_METERS * cosLat));

        double south = Math.max(MapConstants.MIN_LATITUDE, lat - latDelta);
        double north = Math.min(MapConstants.MAX_LATITUDE, lat + latDelta);
        double west = Math.max(MapConstants.MIN_LONGITUDE, lon - lonDelta);
        double east = Math.min(MapConstants.MAX_LONGITUDE, lon + lonDelta);

        return new BoundingBox(round6(south), round6(west), round6(north), round6(east));
    }

    /**
     * Calculates spherical/geodesic polygon area, perimeter, centroid, and GeoJSON using JTS Topology Suite
     * and local sinusoidal/equal-area projection for accurate metric measurements.
     */
    public static AreaCalculationResult calculatePolygonArea(List<Coordinates> vertices) {
        List<Coordinates> closed = new ArrayList<>(vertices);
        Coordinates first = vertices.getFirst();
        Coordinates last = vertices.getLast();
        if (Double.compare(first.latitude(), last.latitude()) != 0
                || Double.compare(first.longitude(), last.longitude()) != 0) {
            closed.add(first);
        }

        // Build JTS Polygon in WGS84 (x = longitude, y = latitude)
        Coordinate[] jtsCoords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            jtsCoords[i] = new Coordinate(closed.get(i).longitude(), closed.get(i).latitude());
        }
        Polygon wgs84Polygon = GEOMETRY_FACTORY.createPolygon(jtsCoords);
        boolean isValid = wgs84Polygon.isValid();
        Point centroidPoint = wgs84Polygon.getCentroid();
        Coordinates centroid = new Coordinates(
                round6(centroidPoint.getY()),
                round6(centroidPoint.getX())
        );

        Envelope env = wgs84Polygon.getEnvelopeInternal();
        BoundingBox bbox = new BoundingBox(
                round6(env.getMinY()),
                round6(env.getMinX()),
                round6(env.getMaxY()),
                round6(env.getMaxX())
        );

        // Project vertices into local planar meters relative to centroid for JTS metric area calculation
        double refLatRad = Math.toRadians(centroid.latitude());
        double refLonRad = Math.toRadians(centroid.longitude());
        Coordinate[] metricCoords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            double latRad = Math.toRadians(closed.get(i).latitude());
            double lonRad = Math.toRadians(closed.get(i).longitude());
            double xMeters = (lonRad - refLonRad) * Math.cos((latRad + refLatRad) / 2.0) * MapConstants.EARTH_RADIUS_METERS;
            double yMeters = (latRad - refLatRad) * MapConstants.EARTH_RADIUS_METERS;
            metricCoords[i] = new Coordinate(xMeters, yMeters);
        }
        Polygon metricPolygon = GEOMETRY_FACTORY.createPolygon(metricCoords);
        double areaSqMeters = Math.abs(metricPolygon.getArea());

        // Geodesic perimeter using Haversine along consecutive edges
        double perimeterMeters = 0.0;
        for (int i = 0; i < closed.size() - 1; i++) {
            perimeterMeters += haversineDistanceMeters(closed.get(i), closed.get(i + 1));
        }

        double areaSqKm = areaSqMeters / 1_000_000.0;
        double areaHectares = areaSqMeters / 10_000.0;
        double areaAcres = areaSqMeters * 0.000247105381;
        double perimeterKm = perimeterMeters / 1_000.0;

        Map<String, Object> geoJson = toGeoJsonPolygon(closed);

        return new AreaCalculationResult(
                round2(areaSqMeters),
                round6(areaSqKm),
                round4(areaHectares),
                round4(areaAcres),
                round2(perimeterMeters),
                round4(perimeterKm),
                centroid,
                bbox,
                vertices.size(),
                isValid,
                geoJson,
                closed
        );
    }

    public static Map<String, Object> toGeoJsonPolygon(List<Coordinates> closedVertices) {
        List<List<Double>> ring = new ArrayList<>(closedVertices.size());
        for (Coordinates c : closedVertices) {
            ring.add(List.of(round6(c.longitude()), round6(c.latitude())));
        }
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", List.of(ring));
        return geometry;
    }

    public static MapTileData computeTileData(double lat, double lon, int zoom, String tileUrlTemplate, String provider, String attribution) {
        int n = 1 << zoom;
        int xTile = (int) Math.floor((lon + 180.0) / 360.0 * n);
        double latRad = Math.toRadians(lat);
        int yTile = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);

        xTile = Math.max(0, Math.min(n - 1, xTile));
        yTile = Math.max(0, Math.min(n - 1, yTile));

        double west = tile2lon(xTile, zoom);
        double east = tile2lon(xTile + 1, zoom);
        double north = tile2lat(yTile, zoom);
        double south = tile2lat(yTile + 1, zoom);

        BoundingBox tileBbox = new BoundingBox(round6(south), round6(west), round6(north), round6(east));
        double spanLat = Math.abs(north - south);
        double spanLon = Math.abs(east - west);
        BoundingBox viewportBbox = new BoundingBox(
                round6(Math.max(MapConstants.MIN_LATITUDE, lat - spanLat)),
                round6(Math.max(MapConstants.MIN_LONGITUDE, lon - spanLon)),
                round6(Math.min(MapConstants.MAX_LATITUDE, lat + spanLat)),
                round6(Math.min(MapConstants.MAX_LONGITUDE, lon + spanLon))
        );

        String tileUrl = tileUrlTemplate
                .replace("{z}", String.valueOf(zoom))
                .replace("{x}", String.valueOf(xTile))
                .replace("{y}", String.valueOf(yTile));

        List<Coordinates> bboxClosed = List.of(
                new Coordinates(tileBbox.southLat(), tileBbox.westLon()),
                new Coordinates(tileBbox.southLat(), tileBbox.eastLon()),
                new Coordinates(tileBbox.northLat(), tileBbox.eastLon()),
                new Coordinates(tileBbox.northLat(), tileBbox.westLon()),
                new Coordinates(tileBbox.southLat(), tileBbox.westLon())
        );

        return new MapTileData(
                new Coordinates(lat, lon),
                zoom,
                xTile,
                yTile,
                tileUrl,
                tileUrlTemplate,
                tileBbox,
                viewportBbox,
                toGeoJsonPolygon(bboxClosed),
                attribution,
                "Respect tile server caching and usage policies (User-Agent header required; bulk downloading prohibited).",
                provider
        );
    }

    private static double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180.0;
    }

    private static double tile2lat(int y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     * Decodes a Google-encoded polyline string into a list of GPS coordinates.
     */
    public static List<Coordinates> decodePolyline(String encoded) {
        List<Coordinates> poly = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return poly;
        }
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            if (index >= len) {
                break;
            }
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new Coordinates(round6(lat / 1E5), round6(lng / 1E5)));
        }
        return poly;
    }

    public static String formatDistance(double meters) {
        if (meters < 1000.0) {
            return String.format(Locale.US, "%.0f m", meters);
        }
        return String.format(Locale.US, "%.2f km", meters / 1000.0);
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " sec";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + " hr " + minutes + " min";
        }
        return minutes + " min";
    }

    public static Coordinates parseCoordinatesIfPresent(String locationStr) {
        if (locationStr == null || locationStr.isBlank()) {
            return null;
        }
        String trimmed = locationStr.trim();
        String[] parts = trimmed.split(",");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                    return new Coordinates(lat, lon);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public static double round4(double val) {
        return Math.round(val * 10_000.0) / 10_000.0;
    }

    public static double round6(double val) {
        return Math.round(val * 1_000_000.0) / 1_000_000.0;
    }
}
