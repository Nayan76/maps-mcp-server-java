package com.example.mapsmcp.providers;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProviderSelector {

    private final MapsProperties properties;
    private final List<PlacesProvider> placesProviders;
    private final List<GeocodingProvider> geocodingProviders;
    private final List<DirectionsProvider> directionsProviders;
    private final List<DistanceMatrixProvider> distanceMatrixProviders;
    private final List<ElevationProvider> elevationProviders;
    private final List<TrafficProvider> trafficProviders;
    private final List<MapTileProvider> mapTileProviders;

    public ProviderSelector(MapsProperties properties,
                            List<PlacesProvider> placesProviders,
                            List<GeocodingProvider> geocodingProviders,
                            List<DirectionsProvider> directionsProviders,
                            List<DistanceMatrixProvider> distanceMatrixProviders,
                            List<ElevationProvider> elevationProviders,
                            List<TrafficProvider> trafficProviders,
                            List<MapTileProvider> mapTileProviders) {
        this.properties = properties;
        this.placesProviders = placesProviders;
        this.geocodingProviders = geocodingProviders;
        this.directionsProviders = directionsProviders;
        this.distanceMatrixProviders = distanceMatrixProviders;
        this.elevationProviders = elevationProviders;
        this.trafficProviders = trafficProviders;
        this.mapTileProviders = mapTileProviders;
    }

    public PlacesProvider selectPlacesProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (PlacesProvider p : placesProviders) {
            if (matches(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        if (properties.isFallbackEnabled()) {
            for (PlacesProvider p : placesProviders) {
                if (p.getProviderType() == ProviderType.OPENSTREETMAP && p.isConfigured()) {
                    return p;
                }
            }
        }
        return placesProviders.getFirst();
    }

    public GeocodingProvider selectGeocodingProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (GeocodingProvider p : geocodingProviders) {
            if (matches(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        if (properties.isFallbackEnabled()) {
            for (GeocodingProvider p : geocodingProviders) {
                if (p.getProviderType() == ProviderType.NOMINATIM && p.isConfigured()) {
                    return p;
                }
            }
        }
        return geocodingProviders.getFirst();
    }

    public DirectionsProvider selectDirectionsProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (DirectionsProvider p : directionsProviders) {
            if (matchesRouting(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        if (properties.isFallbackEnabled()) {
            for (DirectionsProvider p : directionsProviders) {
                if (p.getProviderType() == ProviderType.OSRM && p.isConfigured()) {
                    return p;
                }
            }
        }
        return directionsProviders.getFirst();
    }

    public DistanceMatrixProvider selectDistanceMatrixProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (DistanceMatrixProvider p : distanceMatrixProviders) {
            if (matchesRouting(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        if (properties.isFallbackEnabled()) {
            for (DistanceMatrixProvider p : distanceMatrixProviders) {
                if (p.getProviderType() == ProviderType.OSRM && p.isConfigured()) {
                    return p;
                }
            }
        }
        return distanceMatrixProviders.getFirst();
    }

    public ElevationProvider selectElevationProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (ElevationProvider p : elevationProviders) {
            if (matches(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        if (properties.isFallbackEnabled()) {
            for (ElevationProvider p : elevationProviders) {
                if (p.getProviderType() == ProviderType.OPENSTREETMAP && p.isConfigured()) {
                    return p;
                }
            }
        }
        return elevationProviders.getFirst();
    }

    public TrafficProvider selectTrafficProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (TrafficProvider p : trafficProviders) {
            if (p.getProviderType() == primary && p.isConfigured()) {
                return p;
            }
        }
        throw new UnsupportedCapabilityException(
                primary.getValue(),
                "get_traffic_conditions",
                "Real-time traffic conditions require a provider with live traffic telemetry (such as Google Maps Platform with GOOGLE_MAPS_API_KEY configured). OpenStreetMap/OSRM does not provide live traffic feeds; this server never fabricates real-time traffic data."
        );
    }

    public MapTileProvider selectMapTileProvider() {
        ProviderType primary = properties.getPrimaryProviderType();
        for (MapTileProvider p : mapTileProviders) {
            if (matches(p.getProviderType(), primary) && p.isConfigured()) {
                return p;
            }
        }
        for (MapTileProvider p : mapTileProviders) {
            if (p.getProviderType() == ProviderType.OPENSTREETMAP) {
                return p;
            }
        }
        return mapTileProviders.getFirst();
    }

    private boolean matches(ProviderType candidate, ProviderType target) {
        if (candidate == target) return true;
        if ((target == ProviderType.OPENSTREETMAP || target == ProviderType.NOMINATIM || target == ProviderType.OSRM)
                && (candidate == ProviderType.OPENSTREETMAP || candidate == ProviderType.NOMINATIM)) {
            return true;
        }
        return false;
    }

    private boolean matchesRouting(ProviderType candidate, ProviderType target) {
        if (candidate == target) return true;
        if ((target == ProviderType.OPENSTREETMAP || target == ProviderType.NOMINATIM || target == ProviderType.OSRM)
                && candidate == ProviderType.OSRM) {
            return true;
        }
        return false;
    }
}
