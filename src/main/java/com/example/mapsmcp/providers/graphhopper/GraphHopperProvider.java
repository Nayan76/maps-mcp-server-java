package com.example.mapsmcp.providers.graphhopper;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.RouteOption;
import com.example.mapsmcp.models.RouteStep;
import com.example.mapsmcp.providers.DirectionsProvider;
import com.example.mapsmcp.providers.nominatim.NominatimGeocodingProvider;
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

@Component
public class GraphHopperProvider implements DirectionsProvider {

    private final RestClient restClient;
    private final MapsProperties properties;
    private final NominatimGeocodingProvider nominatimProvider;
    private final ObjectMapper objectMapper;

    public GraphHopperProvider(RestClient.Builder restClientBuilder,
                               MapsProperties properties,
                               NominatimGeocodingProvider nominatimProvider,
                               ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.nominatimProvider = nominatimProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GRAPHHOPPER;
    }

    @Override
    public String getProviderName() {
        return "GraphHopper Routing Engine";
    }

    @Override
    public boolean isConfigured() {
        return properties.getGraphhopper().hasApiKey();
    }

    @Override
    @Retry(name = "providerRetry")
    public DirectionsResponse getDirections(DirectionsRequest request) {
        if (!isConfigured()) {
            throw new ProviderApiException(getProviderName(), 401, "GRAPHHOPPER_API_KEY is not configured.");
        }
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        if (mode == TravelMode.TRANSIT) {
            throw new UnsupportedCapabilityException(getProviderName(), "transit_routing",
                    "GraphHopper standard routing endpoint does not support public transit schedules.");
        }

        String profile = switch (mode) {
            case DRIVING -> "car";
            case WALKING -> "foot";
            case CYCLING -> "bike";
            case TRANSIT -> "car";
        };

        Coordinates start = resolveLocation(request.origin());
        Coordinates end = resolveLocation(request.destination());

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGraphhopper().getBaseUrl())
                    .path("/route")
                    .queryParam("point", start.latitude() + "," + start.longitude());

            if (request.waypoints() != null) {
                for (String wp : request.waypoints()) {
                    if (wp != null && !wp.isBlank()) {
                        Coordinates wc = resolveLocation(wp);
                        builder.queryParam("point", wc.latitude() + "," + wc.longitude());
                    }
                }
            }

            builder.queryParam("point", end.latitude() + "," + end.longitude())
                    .queryParam("profile", profile)
                    .queryParam("points_encoded", "true")
                    .queryParam("instructions", "true")
                    .queryParam("key", properties.getGraphhopper().getApiKey());

            URI uri = builder.build().encode().toUri();
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode paths = root.path("paths");
            List<RouteOption> routes = new ArrayList<>();
            if (paths.isArray()) {
                int idx = 0;
                for (JsonNode p : paths) {
                    double dist = GeoMathUtils.round2(p.path("distance").asDouble());
                    long durSec = Math.round(p.path("time").asDouble() / 1000.0);
                    String poly = p.path("points").asText(null);
                    List<RouteStep> steps = new ArrayList<>();
                    int sIdx = 1;
                    if (p.path("instructions").isArray()) {
                        for (JsonNode inst : p.path("instructions")) {
                            double sDist = GeoMathUtils.round2(inst.path("distance").asDouble());
                            long sDur = Math.round(inst.path("time").asDouble() / 1000.0);
                            steps.add(new RouteStep(
                                    sIdx++,
                                    inst.path("text").asText("Continue"),
                                    sDist, GeoMathUtils.formatDistance(sDist),
                                    sDur, GeoMathUtils.formatDuration(sDur),
                                    null, null, null,
                                    inst.path("street_name").asText(null),
                                    mode.getValue()
                            ));
                        }
                    }
                    routes.add(new RouteOption(
                            idx++, "GraphHopper Route " + idx,
                            dist, GeoMathUtils.formatDistance(dist),
                            durSec, GeoMathUtils.formatDuration(durSec),
                            null, null, start, request.origin(), end, request.destination(),
                            null, poly, GeoMathUtils.decodePolyline(poly), steps, List.of(), null, List.of()
                    ));
                }
            }
            if (routes.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No routes found from GraphHopper.");
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
                    "GraphHopper routing failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    private Coordinates resolveLocation(String loc) {
        Coordinates parsed = GeoMathUtils.parseCoordinatesIfPresent(loc);
        if (parsed != null) return parsed;
        List<GeocodeResult> g = nominatimProvider.geocodeAddress(loc, null, 1);
        if (g.isEmpty()) {
            throw new ProviderApiException(getProviderName(), 404, "Could not resolve location: " + loc);
        }
        return g.getFirst().coordinates();
    }
}
