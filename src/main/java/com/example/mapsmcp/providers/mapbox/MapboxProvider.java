package com.example.mapsmcp.providers.mapbox;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.models.AddressDetails;
import com.example.mapsmcp.models.BoundingBox;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.models.RouteOption;
import com.example.mapsmcp.models.RouteStep;
import com.example.mapsmcp.providers.DirectionsProvider;
import com.example.mapsmcp.providers.GeocodingProvider;
import com.example.mapsmcp.providers.MapTileProvider;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.utils.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class MapboxProvider implements GeocodingProvider, DirectionsProvider, MapTileProvider {

    private final RestClient restClient;
    private final MapsProperties properties;
    private final ObjectMapper objectMapper;

    public MapboxProvider(RestClient.Builder restClientBuilder,
                          MapsProperties properties,
                          ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.MAPBOX;
    }

    @Override
    public String getProviderName() {
        return "Mapbox Platform";
    }

    @Override
    public boolean isConfigured() {
        return properties.getMapbox().hasAccessToken();
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new ProviderApiException(getProviderName(), 401,
                    "MAPBOX_ACCESS_TOKEN is not configured.");
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public List<GeocodeResult> geocodeAddress(String address, String countryCode, int limit) {
        ensureConfigured();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getMapbox().getBaseUrl())
                    .path("/geocoding/v5/mapbox.places/{query}.json")
                    .queryParam("limit", Math.min(limit, 10))
                    .queryParam("access_token", properties.getMapbox().getAccessToken());

            if (countryCode != null && !countryCode.isBlank()) {
                builder.queryParam("country", countryCode.trim().toLowerCase());
            }

            URI uri = builder.buildAndExpand(address).encode().toUri();
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            List<GeocodeResult> results = new ArrayList<>();
            if (root.path("features").isArray()) {
                for (JsonNode f : root.path("features")) {
                    results.add(parseFeature(f));
                }
            }
            return results;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Mapbox geocoding failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public GeocodeResult reverseGeocode(Coordinates coordinates) {
        ensureConfigured();
        try {
            String coordQuery = String.format(Locale.US, "%.6f,%.6f", coordinates.longitude(), coordinates.latitude());
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getMapbox().getBaseUrl())
                    .path("/geocoding/v5/mapbox.places/{coords}.json")
                    .queryParam("limit", 1)
                    .queryParam("access_token", properties.getMapbox().getAccessToken())
                    .buildAndExpand(coordQuery)
                    .encode()
                    .toUri();

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No reverse geocode results found in Mapbox.");
            }
            return parseFeature(features.get(0));
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Mapbox reverse geocoding failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public CountryCityInfo getCountryAndCityInfo(String query, String countryCode) {
        List<GeocodeResult> list = geocodeAddress(query, countryCode, 1);
        if (list.isEmpty()) {
            throw new ProviderApiException(getProviderName(), 404, "No location found for query: " + query);
        }
        GeocodeResult gr = list.getFirst();
        AddressDetails addr = gr.addressDetails();
        return new CountryCityInfo(
                query,
                gr.formattedAddress(),
                gr.locationType(),
                addr != null ? addr.city() : null,
                addr != null ? addr.state() : null,
                addr != null ? addr.country() : null,
                addr != null ? addr.countryCode() : null,
                gr.coordinates(),
                gr.boundingBox(),
                null,
                null,
                null,
                null,
                null,
                null,
                getProviderName()
        );
    }

    @Override
    @Retry(name = "providerRetry")
    public DirectionsResponse getDirections(DirectionsRequest request) {
        ensureConfigured();
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        if (mode == TravelMode.TRANSIT) {
            throw new UnsupportedCapabilityException(getProviderName(), "transit_routing",
                    "Mapbox Directions API does not support public transit schedules.");
        }
        String profile = switch (mode) {
            case DRIVING -> "driving-traffic";
            case WALKING -> "walking";
            case CYCLING -> "cycling";
            case TRANSIT -> "driving";
        };

        List<Coordinates> points = new ArrayList<>();
        points.add(resolveCoordinates(request.origin()));
        if (request.waypoints() != null) {
            for (String wp : request.waypoints()) {
                if (wp != null && !wp.isBlank()) {
                    points.add(resolveCoordinates(wp));
                }
            }
        }
        points.add(resolveCoordinates(request.destination()));

        String coords = points.stream()
                .map(c -> String.format(Locale.US, "%.6f,%.6f", c.longitude(), c.latitude()))
                .collect(Collectors.joining(";"));

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getMapbox().getBaseUrl())
                    .path("/directions/v5/mapbox/{profile}/{coords}")
                    .queryParam("alternatives", request.alternatives())
                    .queryParam("geometries", "polyline")
                    .queryParam("overview", "full")
                    .queryParam("steps", "true")
                    .queryParam("access_token", properties.getMapbox().getAccessToken())
                    .buildAndExpand(profile, coords)
                    .encode()
                    .toUri();

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode routesNode = root.path("routes");
            List<RouteOption> routes = new ArrayList<>();
            if (routesNode.isArray()) {
                int idx = 0;
                for (JsonNode r : routesNode) {
                    double dist = GeoMathUtils.round2(r.path("distance").asDouble());
                    long dur = Math.round(r.path("duration").asDouble());
                    String poly = r.path("geometry").asText(null);
                    List<RouteStep> steps = new ArrayList<>();
                    int stepIdx = 1;
                    if (r.path("legs").isArray()) {
                        for (JsonNode leg : r.path("legs")) {
                            if (leg.path("steps").isArray()) {
                                for (JsonNode s : leg.path("steps")) {
                                    double sDist = GeoMathUtils.round2(s.path("distance").asDouble());
                                    long sDur = Math.round(s.path("duration").asDouble());
                                    String instruction = s.path("maneuver").path("instruction").asText("Continue");
                                    steps.add(new RouteStep(
                                            stepIdx++, instruction, sDist, GeoMathUtils.formatDistance(sDist),
                                            sDur, GeoMathUtils.formatDuration(sDur), null, null,
                                            s.path("maneuver").path("type").asText(null),
                                            s.path("name").asText(null), mode.getValue()
                                    ));
                                }
                            }
                        }
                    }
                    routes.add(new RouteOption(
                            idx++,
                            r.path("legs").path(0).path("summary").asText("Mapbox Route"),
                            dist, GeoMathUtils.formatDistance(dist),
                            dur, GeoMathUtils.formatDuration(dur),
                            mode == TravelMode.DRIVING ? dur : null,
                            mode == TravelMode.DRIVING ? GeoMathUtils.formatDuration(dur) : null,
                            points.getFirst(), request.origin(),
                            points.getLast(), request.destination(),
                            null, poly, GeoMathUtils.decodePolyline(poly), steps, List.of(), null, List.of()
                    ));
                }
            }
            if (routes.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No routes found from Mapbox.");
            }
            return new DirectionsResponse(
                    request.origin(), request.destination(), mode.getValue(),
                    routes.size(), routes.getFirst(),
                    routes.size() > 1 ? routes.subList(1, routes.size()) : List.of(),
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Mapbox directions failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public MapTileData getMapTileData(Coordinates center, int zoomLevel) {
        String template = properties.getMapbox().getBaseUrl()
                + "/styles/v1/mapbox/streets-v12/tiles/256/{z}/{x}/{y}?access_token=***REDACTED***";
        return GeoMathUtils.computeTileData(
                center.latitude(), center.longitude(), zoomLevel,
                template, getProviderName(), "© Mapbox © OpenStreetMap"
        );
    }

    private Coordinates resolveCoordinates(String input) {
        Coordinates parsed = GeoMathUtils.parseCoordinatesIfPresent(input);
        if (parsed != null) return parsed;
        List<GeocodeResult> g = geocodeAddress(input, null, 1);
        if (g.isEmpty()) {
            throw new ProviderApiException(getProviderName(), 404, "Could not geocode location: " + input);
        }
        return g.getFirst().coordinates();
    }

    private GeocodeResult parseFeature(JsonNode f) {
        JsonNode center = f.path("center");
        Coordinates coords = new Coordinates(center.get(1).asDouble(), center.get(0).asDouble());
        String placeName = f.path("place_name").asText("");
        BoundingBox bbox = null;
        if (f.path("bbox").isArray() && f.path("bbox").size() == 4) {
            JsonNode b = f.path("bbox");
            bbox = new BoundingBox(b.get(1).asDouble(), b.get(0).asDouble(), b.get(3).asDouble(), b.get(2).asDouble());
        }
        AddressDetails addr = new AddressDetails(placeName, null, f.path("text").asText(null), null, null, null, null, null, null, null);
        return new GeocodeResult(
                f.path("id").asText("mapbox"),
                placeName,
                coords,
                addr,
                bbox,
                f.path("place_type").path(0).asText("place"),
                null,
                getProviderName()
        );
    }
}
