package com.example.mapsmcp.providers.openstreetmap;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.providers.ElevationProvider;
import com.example.mapsmcp.utils.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class OpenMeteoElevationTimezoneProvider implements ElevationProvider {

    private final RestClient restClient;
    private final MapsProperties properties;
    private final ObjectMapper objectMapper;

    public OpenMeteoElevationTimezoneProvider(RestClient.Builder restClientBuilder,
                                              MapsProperties properties,
                                              ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENSTREETMAP;
    }

    @Override
    public String getProviderName() {
        return "Open-Meteo Elevation & Timezone Service";
    }

    @Override
    public boolean isConfigured() {
        return properties.getOpenElevation().getBaseUrl() != null
                && !properties.getOpenElevation().getBaseUrl().isBlank();
    }

    @Override
    @Retry(name = "providerRetry")
    public List<ElevationPoint> getElevation(List<Coordinates> locations) {
        try {
            String latitudes = locations.stream()
                    .map(c -> String.format(Locale.US, "%.6f", c.latitude()))
                    .collect(Collectors.joining(","));
            String longitudes = locations.stream()
                    .map(c -> String.format(Locale.US, "%.6f", c.longitude()))
                    .collect(Collectors.joining(","));

            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getOpenElevation().getBaseUrl())
                    .path("/v1/elevation")
                    .queryParam("latitude", latitudes)
                    .queryParam("longitude", longitudes)
                    .build()
                    .encode()
                    .toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode elevations = root.path("elevation");
            List<ElevationPoint> results = new ArrayList<>();
            if (elevations.isArray()) {
                for (int i = 0; i < Math.min(locations.size(), elevations.size()); i++) {
                    results.add(new ElevationPoint(
                            locations.get(i),
                            elevations.get(i).asDouble(),
                            90.0, // Copernicus DEM GLO-90 resolution (90 meters)
                            getProviderName()
                    ));
                }
            }
            return results;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Failed to retrieve elevation data: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public TimezoneInfo getTimezone(Coordinates coordinates, Long timestampSeconds) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getOpenElevation().getBaseUrl())
                    .path("/v1/forecast")
                    .queryParam("latitude", coordinates.latitude())
                    .queryParam("longitude", coordinates.longitude())
                    .queryParam("timezone", "auto")
                    .queryParam("forecast_days", 1)
                    .build()
                    .encode()
                    .toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String tzId = root.path("timezone").asText("UTC");

            Instant targetInstant = timestampSeconds != null ? Instant.ofEpochSecond(timestampSeconds) : Instant.now();
            ZoneId zoneId = ZoneId.of(tzId);
            ZoneRules rules = zoneId.getRules();
            int totalOffsetSeconds = rules.getOffset(targetInstant).getTotalSeconds();
            int dstOffsetSeconds = (int) rules.getDaylightSavings(targetInstant).getSeconds();
            int rawOffsetSeconds = totalOffsetSeconds - dstOffsetSeconds;

            ZonedDateTime zdt = targetInstant.atZone(zoneId);
            String displayName = zoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            return new TimezoneInfo(
                    coordinates,
                    tzId,
                    displayName,
                    rawOffsetSeconds,
                    dstOffsetSeconds,
                    zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    getProviderName()
            );
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Failed to determine timezone: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }
}
