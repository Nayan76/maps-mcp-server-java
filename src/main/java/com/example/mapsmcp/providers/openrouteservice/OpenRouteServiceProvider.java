package com.example.mapsmcp.providers.openrouteservice;

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
import java.util.Locale;

@Component
public class OpenRouteServiceProvider implements DirectionsProvider {

    private final RestClient restClient;
    private final MapsProperties properties;
    private final NominatimGeocodingProvider nominatimProvider;
    private final ObjectMapper objectMapper;

    public OpenRouteServiceProvider(RestClient.Builder restClientBuilder,
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
        return ProviderType.OPENROUTESERVICE;
    }

    @Override
    public String getProviderName() {
        return "OpenRouteService";
    }

    @Override
    public boolean isConfigured() {
        return properties.getOpenrouteservice().hasApiKey();
    }

    @Override
    @Retry(name = "providerRetry")
    public DirectionsResponse getDirections(DirectionsRequest request) {
        if (!isConfigured()) {
            throw new ProviderApiException(getProviderName(), 401, "OPENROUTESERVICE_API_KEY is not configured.");
        }
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        if (mode == TravelMode.TRANSIT) {
            throw new UnsupportedCapabilityException(getProviderName(), "transit_routing",
                    "OpenRouteService does not support public transit schedules.");
        }

        String profile = switch (mode) {
            case DRIVING -> "driving-car";
            case WALKING -> "foot-walking";
            case CYCLING -> "cycling-regular";
            case TRANSIT -> "driving-car";
        };

        Coordinates start = resolveLocation(request.origin());
        Coordinates end = resolveLocation(request.destination());

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getOpenrouteservice().getBaseUrl())
                    .path("/v2/directions/{profile}")
                    .queryParam("api_key", properties.getOpenrouteservice().getApiKey())
                    .queryParam("start", String.format(Locale.US, "%.6f,%.6f", start.longitude(), start.latitude()))
                    .queryParam("end", String.format(Locale.US, "%.6f,%.6f", end.longitude(), end.latitude()))
                    .buildAndExpand(profile)
                    .encode()
                    .toUri();

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No routes found from OpenRouteService.");
            }

            JsonNode f = features.get(0);
            JsonNode summaryNode = f.path("properties").path("summary");
            double dist = GeoMathUtils.round2(summaryNode.path("distance").asDouble());
            long dur = Math.round(summaryNode.path("duration").asDouble());

            List<RouteStep> steps = new ArrayList<>();
            int sIdx = 1;
            JsonNode segments = f.path("properties").path("segments");
            if (segments.isArray()) {
                for (JsonNode seg : segments) {
                    if (seg.path("steps").isArray()) {
                        for (JsonNode s : seg.path("steps")) {
                            double sDist = GeoMathUtils.round2(s.path("distance").asDouble());
                            long sDur = Math.round(s.path("duration").asDouble());
                            steps.add(new RouteStep(
                                    sIdx++,
                                    s.path("instruction").asText("Continue"),
                                    sDist, GeoMathUtils.formatDistance(sDist),
                                    sDur, GeoMathUtils.formatDuration(sDur),
                                    null, null, null,
                                    s.path("name").asText(null),
                                    mode.getValue()
                            ));
                        }
                    }
                }
            }

            List<Coordinates> geomCoords = new ArrayList<>();
            JsonNode coordsArr = f.path("geometry").path("coordinates");
            if (coordsArr.isArray()) {
                for (JsonNode c : coordsArr) {
                    if (c.isArray() && c.size() >= 2) {
                        geomCoords.add(new Coordinates(c.get(1).asDouble(), c.get(0).asDouble()));
                    }
                }
            }

            RouteOption primary = new RouteOption(
                    0, "OpenRouteService Route",
                    dist, GeoMathUtils.formatDistance(dist),
                    dur, GeoMathUtils.formatDuration(dur),
                    null, null, start, request.origin(), end, request.destination(),
                    null, null, geomCoords, steps, List.of(), null, List.of()
            );

            return new DirectionsResponse(
                    request.origin(), request.destination(), mode.getValue(),
                    1, primary, List.of(), getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "OpenRouteService directions failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
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
