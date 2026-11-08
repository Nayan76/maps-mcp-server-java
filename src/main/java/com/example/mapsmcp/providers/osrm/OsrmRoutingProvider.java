package com.example.mapsmcp.providers.osrm;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.DistanceMatrixElement;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.RouteOption;
import com.example.mapsmcp.models.RouteStep;
import com.example.mapsmcp.providers.DirectionsProvider;
import com.example.mapsmcp.providers.DistanceMatrixProvider;
import com.example.mapsmcp.providers.nominatim.NominatimGeocodingProvider;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.utils.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class OsrmRoutingProvider implements DirectionsProvider, DistanceMatrixProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRoutingProvider.class);

    private final RestClient restClient;
    private final MapsProperties properties;
    private final NominatimGeocodingProvider nominatimProvider;
    private final ObjectMapper objectMapper;

    public OsrmRoutingProvider(RestClient.Builder restClientBuilder,
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
        return ProviderType.OSRM;
    }

    @Override
    public String getProviderName() {
        return "OSRM (Open Source Routing Machine)";
    }

    @Override
    public boolean isConfigured() {
        return properties.getOsrm().getBaseUrl() != null && !properties.getOsrm().getBaseUrl().isBlank();
    }

    @Override
    @Retry(name = "providerRetry")
    public DirectionsResponse getDirections(DirectionsRequest request) {
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        if (mode == TravelMode.TRANSIT) {
            throw new UnsupportedCapabilityException(
                    getProviderName(),
                    "transit_routing",
                    "OSRM does not support public transit schedules. Use MAP_PROVIDER=google for public transit directions."
            );
        }

        List<Coordinates> points = new ArrayList<>();
        points.add(resolveLocation(request.origin()));
        if (request.waypoints() != null) {
            for (String wp : request.waypoints()) {
                if (wp != null && !wp.isBlank()) {
                    points.add(resolveLocation(wp));
                }
            }
        }
        points.add(resolveLocation(request.destination()));

        String coordsPath = points.stream()
                .map(c -> String.format(Locale.US, "%.6f,%.6f", c.longitude(), c.latitude()))
                .collect(Collectors.joining(";"));

        String osrmProfile = switch (mode) {
            case DRIVING -> "driving";
            case WALKING -> "foot";
            case CYCLING -> "bike";
            case TRANSIT -> "driving";
        };

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getOsrm().getBaseUrl())
                    .path("/route/v1/{profile}/{coords}")
                    .queryParam("overview", "full")
                    .queryParam("geometries", "polyline")
                    .queryParam("steps", "true")
                    .queryParam("alternatives", request.alternatives() ? "true" : "false")
                    .buildAndExpand(osrmProfile, coordsPath)
                    .encode()
                    .toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String code = root.path("code").asText("");
            if (!"Ok".equalsIgnoreCase(code)) {
                throw new ProviderApiException(getProviderName(), 400,
                        "OSRM returned status '" + code + "': " + root.path("message").asText("No route found"));
            }

            JsonNode routesNode = root.path("routes");
            List<RouteOption> allRoutes = new ArrayList<>();
            if (routesNode.isArray()) {
                int idx = 0;
                for (JsonNode rNode : routesNode) {
                    allRoutes.add(parseOsrmRoute(rNode, idx++, points.getFirst(), points.getLast(),
                            request.origin(), request.destination(), mode));
                }
            }

            if (allRoutes.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No route found between origin and destination.");
            }

            RouteOption primary = allRoutes.getFirst();
            List<RouteOption> alternatives = allRoutes.size() > 1 ? allRoutes.subList(1, allRoutes.size()) : List.of();

            return new DirectionsResponse(
                    request.origin(),
                    request.destination(),
                    mode.getValue(),
                    allRoutes.size(),
                    primary,
                    alternatives,
                    getProviderName()
            );
        } catch (ProviderApiException | UnsupportedCapabilityException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSRM getDirections failed: {}", SecretMasker.maskUrlSecrets(e.getMessage()));
            throw new ProviderApiException(getProviderName(),
                    "Failed to compute directions via OSRM: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        if (mode == TravelMode.TRANSIT) {
            throw new UnsupportedCapabilityException(
                    getProviderName(),
                    "transit_distance_matrix",
                    "OSRM does not support public transit distance matrices. Use MAP_PROVIDER=google for transit matrices."
            );
        }

        List<Coordinates> originCoords = request.origins().stream().map(this::resolveLocation).toList();
        List<Coordinates> destCoords = request.destinations().stream().map(this::resolveLocation).toList();

        List<Coordinates> allPoints = new ArrayList<>(originCoords.size() + destCoords.size());
        allPoints.addAll(originCoords);
        allPoints.addAll(destCoords);

        String coordsPath = allPoints.stream()
                .map(c -> String.format(Locale.US, "%.6f,%.6f", c.longitude(), c.latitude()))
                .collect(Collectors.joining(";"));

        StringBuilder sourcesIdx = new StringBuilder();
        for (int i = 0; i < originCoords.size(); i++) {
            if (i > 0) sourcesIdx.append(";");
            sourcesIdx.append(i);
        }

        StringBuilder destsIdx = new StringBuilder();
        for (int j = 0; j < destCoords.size(); j++) {
            if (j > 0) destsIdx.append(";");
            destsIdx.append(originCoords.size() + j);
        }

        String osrmProfile = switch (mode) {
            case DRIVING -> "driving";
            case WALKING -> "foot";
            case CYCLING -> "bike";
            case TRANSIT -> "driving";
        };

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getOsrm().getBaseUrl())
                    .path("/table/v1/{profile}/{coords}")
                    .queryParam("sources", sourcesIdx.toString())
                    .queryParam("destinations", destsIdx.toString())
                    .queryParam("annotations", "distance,duration")
                    .buildAndExpand(osrmProfile, coordsPath)
                    .encode()
                    .toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (!"Ok".equalsIgnoreCase(root.path("code").asText(""))) {
                throw new ProviderApiException(getProviderName(), 400,
                        "OSRM table service error: " + root.path("message").asText("Unknown error"));
            }

            JsonNode durations = root.path("durations");
            JsonNode distances = root.path("distances");
            List<DistanceMatrixElement> elements = new ArrayList<>();

            for (int i = 0; i < request.origins().size(); i++) {
                for (int j = 0; j < request.destinations().size(); j++) {
                    JsonNode durNode = durations.path(i).path(j);
                    JsonNode distNode = distances.path(i).path(j);
                    if (durNode.isNull() || durNode.isMissingNode() || distNode.isNull() || distNode.isMissingNode()) {
                        elements.add(new DistanceMatrixElement(
                                i, j, request.origins().get(i), request.destinations().get(j),
                                "ZERO_RESULTS", null, null, null, null, null
                        ));
                    } else {
                        double distMeters = GeoMathUtils.round2(distNode.asDouble());
                        long durSeconds = Math.round(durNode.asDouble());
                        elements.add(new DistanceMatrixElement(
                                i, j,
                                request.origins().get(i),
                                request.destinations().get(j),
                                "OK",
                                distMeters,
                                GeoMathUtils.formatDistance(distMeters),
                                durSeconds,
                                GeoMathUtils.formatDuration(durSeconds),
                                null
                        ));
                    }
                }
            }

            return new DistanceMatrixResponse(
                    request.origins(),
                    request.destinations(),
                    mode.getValue(),
                    elements,
                    getProviderName()
            );
        } catch (ProviderApiException | UnsupportedCapabilityException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Failed to compute distance matrix via OSRM: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    private Coordinates resolveLocation(String location) {
        Coordinates parsed = GeoMathUtils.parseCoordinatesIfPresent(location);
        if (parsed != null) {
            return parsed;
        }
        List<GeocodeResult> geocoded = nominatimProvider.geocodeAddress(location, null, 1);
        if (geocoded.isEmpty() || geocoded.getFirst().coordinates() == null) {
            throw new ProviderApiException(getProviderName(), 404,
                    "Could not resolve location '" + location + "' to GPS coordinates.");
        }
        return geocoded.getFirst().coordinates();
    }

    private RouteOption parseOsrmRoute(JsonNode routeNode, int index,
                                       Coordinates start, Coordinates end,
                                       String startAddr, String endAddr, TravelMode mode) {
        double distanceMeters = GeoMathUtils.round2(routeNode.path("distance").asDouble());
        long durationSeconds = Math.round(routeNode.path("duration").asDouble());
        String geometryPolyline = routeNode.path("geometry").asText(null);
        List<Coordinates> decodedCoords = GeoMathUtils.decodePolyline(geometryPolyline);

        List<RouteStep> steps = new ArrayList<>();
        List<String> legSummaries = new ArrayList<>();
        JsonNode legs = routeNode.path("legs");
        int stepCount = 1;
        if (legs.isArray()) {
            for (JsonNode leg : legs) {
                String legSum = leg.path("summary").asText("");
                if (!legSum.isBlank()) {
                    legSummaries.add(legSum);
                }
                JsonNode stepsNode = leg.path("steps");
                if (stepsNode.isArray()) {
                    for (JsonNode sNode : stepsNode) {
                        double sDist = GeoMathUtils.round2(sNode.path("distance").asDouble());
                        long sDur = Math.round(sNode.path("duration").asDouble());
                        String roadName = sNode.path("name").asText("");
                        JsonNode maneuverNode = sNode.path("maneuver");
                        String maneuverType = maneuverNode.path("type").asText("continue");
                        String modifier = maneuverNode.path("modifier").asText("");
                        String instruction = buildInstruction(maneuverType, modifier, roadName);

                        Coordinates stepStart = null;
                        JsonNode loc = maneuverNode.path("location");
                        if (loc.isArray() && loc.size() == 2) {
                            stepStart = new Coordinates(loc.get(1).asDouble(), loc.get(0).asDouble());
                        }

                        steps.add(new RouteStep(
                                stepCount++,
                                instruction,
                                sDist,
                                GeoMathUtils.formatDistance(sDist),
                                sDur,
                                GeoMathUtils.formatDuration(sDur),
                                stepStart,
                                null,
                                modifier.isBlank() ? maneuverType : maneuverType + "-" + modifier,
                                roadName.isBlank() ? null : roadName,
                                mode.getValue()
                        ));
                    }
                }
            }
        }

        String summary = legSummaries.isEmpty() ? "Route " + (index + 1) : String.join(" -> ", legSummaries);

        return new RouteOption(
                index,
                summary,
                distanceMeters,
                GeoMathUtils.formatDistance(distanceMeters),
                durationSeconds,
                GeoMathUtils.formatDuration(durationSeconds),
                null,
                null,
                start,
                startAddr,
                end,
                endAddr,
                null,
                geometryPolyline,
                decodedCoords.size() > 200 ? decodedCoords.subList(0, 200) : decodedCoords,
                steps,
                List.of(),
                null,
                List.of()
        );
    }

    private String buildInstruction(String type, String modifier, String roadName) {
        String target = (roadName != null && !roadName.isBlank()) ? " onto " + roadName : "";
        return switch (type) {
            case "depart" -> "Depart" + (modifier.isBlank() ? "" : " " + modifier) + target;
            case "arrive" -> "Arrive at destination";
            case "turn" -> "Turn " + (modifier.isBlank() ? "" : modifier) + target;
            case "roundabout", "rotary" -> "Enter roundabout and take exit" + target;
            case "merge" -> "Merge " + modifier + target;
            case "fork" -> "Keep " + modifier + " at fork" + target;
            default -> "Continue" + (modifier.isBlank() ? "" : " " + modifier) + target;
        };
    }
}
