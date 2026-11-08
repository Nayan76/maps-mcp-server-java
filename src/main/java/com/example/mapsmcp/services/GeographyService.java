package com.example.mapsmcp.services;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.models.AreaCalculationResult;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.providers.ElevationProvider;
import com.example.mapsmcp.providers.MapTileProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.validation.GeoValidator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeographyService {

    private final ProviderSelector providerSelector;
    private final GeoValidator geoValidator;

    public GeographyService(ProviderSelector providerSelector, GeoValidator geoValidator) {
        this.providerSelector = providerSelector;
        this.geoValidator = geoValidator;
    }

    public String getActiveElevationProviderName() {
        return providerSelector.selectElevationProvider().getProviderName();
    }

    public String getActiveTileProviderName() {
        return providerSelector.selectMapTileProvider().getProviderName();
    }

    public MapTileData getMapData(Double latitude, Double longitude, Integer zoomLevel) {
        geoValidator.validateCoordinates(latitude, longitude);
        int zoom = geoValidator.validateAndNormalizeZoom(zoomLevel);
        MapTileProvider tileProvider = providerSelector.selectMapTileProvider();
        return tileProvider.getMapTileData(new Coordinates(latitude, longitude), zoom);
    }

    @Cacheable(value = MapConstants.CACHE_ELEVATION, key = "#locations.toString()")
    public List<ElevationPoint> getElevation(List<Coordinates> locations) {
        if (locations == null || locations.isEmpty()) {
            throw new ValidationException("At least one coordinate pair is required for elevation lookup.");
        }
        if (locations.size() > 100) {
            throw new ValidationException("Cannot query more than 100 elevation points in a single request.");
        }
        for (Coordinates c : locations) {
            geoValidator.validateCoordinates(c);
        }
        ElevationProvider provider = providerSelector.selectElevationProvider();
        return provider.getElevation(locations);
    }

    @Cacheable(value = MapConstants.CACHE_TIMEZONE, key = "#latitude + ':' + #longitude + ':' + #timestampSeconds")
    public TimezoneInfo getTimezone(Double latitude, Double longitude, Long timestampSeconds) {
        geoValidator.validateCoordinates(latitude, longitude);
        ElevationProvider provider = providerSelector.selectElevationProvider();
        return provider.getTimezone(new Coordinates(latitude, longitude), timestampSeconds);
    }

    public AreaCalculationResult calculateArea(List<Coordinates> vertices) {
        geoValidator.validatePolygonVertices(vertices);
        return GeoMathUtils.calculatePolygonArea(vertices);
    }
}
