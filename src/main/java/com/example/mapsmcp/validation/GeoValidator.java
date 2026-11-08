package com.example.mapsmcp.validation;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.models.Coordinates;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

@Component
public class GeoValidator {

    public void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ValidationException("Both latitude and longitude must be provided.");
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || latitude < MapConstants.MIN_LATITUDE || latitude > MapConstants.MAX_LATITUDE) {
            throw new ValidationException(
                    "Invalid latitude: " + latitude + ". Must be between -90.0 and 90.0 degrees.");
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude)
                || longitude < MapConstants.MIN_LONGITUDE || longitude > MapConstants.MAX_LONGITUDE) {
            throw new ValidationException(
                    "Invalid longitude: " + longitude + ". Must be between -180.0 and 180.0 degrees.");
        }
    }

    public void validateCoordinates(Coordinates coordinates) {
        if (coordinates == null) {
            throw new ValidationException("Coordinates must not be null.");
        }
        validateCoordinates(coordinates.latitude(), coordinates.longitude());
    }

    public int validateAndNormalizeRadius(Integer radiusMeters) {
        if (radiusMeters == null) {
            return MapConstants.DEFAULT_RADIUS_METERS;
        }
        if (radiusMeters <= 0 || radiusMeters > MapConstants.MAX_RADIUS_METERS) {
            throw new ValidationException(
                    "Invalid radiusMeters: " + radiusMeters + ". Must be between 1 and "
                            + MapConstants.MAX_RADIUS_METERS + " meters.");
        }
        return radiusMeters;
    }

    public int validateAndNormalizeLimit(Integer limit) {
        if (limit == null) {
            return MapConstants.DEFAULT_RESULT_LIMIT;
        }
        if (limit <= 0 || limit > MapConstants.MAX_RESULT_LIMIT) {
            throw new ValidationException(
                    "Invalid limit: " + limit + ". Must be between 1 and " + MapConstants.MAX_RESULT_LIMIT + ".");
        }
        return limit;
    }

    public int validateAndNormalizeZoom(Integer zoomLevel) {
        if (zoomLevel == null) {
            return MapConstants.DEFAULT_ZOOM_LEVEL;
        }
        if (zoomLevel < MapConstants.MIN_ZOOM_LEVEL || zoomLevel > MapConstants.MAX_ZOOM_LEVEL) {
            throw new ValidationException(
                    "Invalid zoomLevel: " + zoomLevel + ". Must be between "
                            + MapConstants.MIN_ZOOM_LEVEL + " and " + MapConstants.MAX_ZOOM_LEVEL + ".");
        }
        return zoomLevel;
    }

    public void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Parameter '" + fieldName + "' is required and must not be blank.");
        }
        if (value.length() > 1000) {
            throw new ValidationException("Parameter '" + fieldName + "' exceeds maximum length of 1000 characters.");
        }
    }

    public void validatePolygonVertices(List<Coordinates> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new ValidationException("At least 3 coordinate vertices are required to calculate polygon area.");
        }
        if (vertices.size() > 5000) {
            throw new ValidationException("Polygon vertex count cannot exceed 5000 points.");
        }
        for (Coordinates vertex : vertices) {
            validateCoordinates(vertex);
        }
    }

    /**
     * Validates a configured provider base URL against SSRF risks.
     * Allows localhost/loopback only when explicitly enabled (e.g., for local OSRM/Nominatim or WireMock tests).
     */
    public void validateProviderUrl(String urlString, boolean allowLocalhost) {
        if (urlString == null || urlString.isBlank()) {
            throw new ValidationException("Provider base URL cannot be blank.");
        }
        try {
            URI uri = URI.create(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new ValidationException("Provider URL must use HTTP or HTTPS scheme: " + urlString);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new ValidationException("Provider URL must contain a valid host: " + urlString);
            }
            if (!allowLocalhost) {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new ValidationException(
                            "SSRF Protection: Provider URL points to internal/private network address (" + host + ").");
                }
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Invalid provider URL '" + urlString + "': " + e.getMessage());
        }
    }
}
