package com.example.mapsmcp.providers.nominatim;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.models.AddressDetails;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.BoundingBox;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.providers.GeocodingProvider;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.utils.SecretMasker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingProvider.class);

    private final RestClient restClient;
    private final MapsProperties properties;
    private final ObjectMapper objectMapper;

    public NominatimGeocodingProvider(RestClient.Builder restClientBuilder,
                                      MapsProperties properties,
                                      ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.NOMINATIM;
    }

    @Override
    public String getProviderName() {
        return "OpenStreetMap Nominatim";
    }

    @Override
    public boolean isConfigured() {
        return properties.getNominatim().getBaseUrl() != null
                && !properties.getNominatim().getBaseUrl().isBlank();
    }

    @Override
    @RateLimiter(name = "nominatim")
    @Retry(name = "providerRetry")
    public List<GeocodeResult> geocodeAddress(String address, String countryCode, int limit) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getNominatim().getBaseUrl())
                    .path("/search")
                    .queryParam("q", address)
                    .queryParam("format", "jsonv2")
                    .queryParam("addressdetails", 1)
                    .queryParam("limit", Math.min(Math.max(1, limit), 20));

            if (countryCode != null && !countryCode.isBlank()) {
                builder.queryParam("countrycodes", countryCode.trim().toLowerCase());
            }

            URI uri = builder.build().encode().toUri();
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            List<GeocodeResult> results = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode item : root) {
                    results.add(parseGeocodeNode(item));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Nominatim geocodeAddress failed: {}", SecretMasker.maskUrlSecrets(e.getMessage()));
            throw new ProviderApiException(getProviderName(), "Failed to geocode address: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @RateLimiter(name = "nominatim")
    @Retry(name = "providerRetry")
    public GeocodeResult reverseGeocode(Coordinates coordinates) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getNominatim().getBaseUrl())
                    .path("/reverse")
                    .queryParam("lat", coordinates.latitude())
                    .queryParam("lon", coordinates.longitude())
                    .queryParam("format", "jsonv2")
                    .queryParam("addressdetails", 1)
                    .build()
                    .encode()
                    .toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.has("error")) {
                String errorMsg = root != null && root.has("error") ? root.get("error").asText() : "No address found";
                throw new ProviderApiException(getProviderName(), 404, errorMsg);
            }
            return parseGeocodeNode(root);
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Nominatim reverseGeocode failed: {}", SecretMasker.maskUrlSecrets(e.getMessage()));
            throw new ProviderApiException(getProviderName(), "Failed to reverse geocode coordinates: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @RateLimiter(name = "nominatim")
    @Retry(name = "providerRetry")
    public CountryCityInfo getCountryAndCityInfo(String query, String countryCode) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getNominatim().getBaseUrl())
                    .path("/search")
                    .queryParam("q", query)
                    .queryParam("format", "jsonv2")
                    .queryParam("addressdetails", 1)
                    .queryParam("extratags", 1)
                    .queryParam("polygon_geojson", 1)
                    .queryParam("limit", 1);

            if (countryCode != null && !countryCode.isBlank()) {
                builder.queryParam("countrycodes", countryCode.trim().toLowerCase());
            }

            URI uri = builder.build().encode().toUri();
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No country or city found for query: " + query);
            }

            JsonNode node = root.get(0);
            GeocodeResult base = parseGeocodeNode(node);
            JsonNode extra = node.path("extratags");

            Long population = null;
            if (extra.hasNonNull("population")) {
                try {
                    population = Long.parseLong(extra.get("population").asText().replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {
                }
            }

            String wikidata = extra.path("wikidata").asText(null);
            String wikipedia = extra.path("wikipedia").asText(null);
            String timezone = extra.path("timezone").asText(null);

            Map<String, Object> geoJson = null;
            if (node.hasNonNull("geojson")) {
                geoJson = objectMapper.convertValue(node.get("geojson"), new TypeReference<>() {});
            }

            Map<String, String> extraMap = new LinkedHashMap<>();
            if (extra.isObject()) {
                extra.fields().forEachRemaining(entry -> extraMap.put(entry.getKey(), entry.getValue().asText()));
            }

            AddressDetails addr = base.addressDetails();
            return new CountryCityInfo(
                    query,
                    base.formattedAddress(),
                    node.path("addresstype").asText(node.path("type").asText("administrative")),
                    addr != null ? addr.city() : null,
                    addr != null ? addr.state() : null,
                    addr != null ? addr.country() : null,
                    addr != null ? addr.countryCode() : null,
                    base.coordinates(),
                    base.boundingBox(),
                    timezone,
                    population,
                    wikidata,
                    wikipedia,
                    geoJson,
                    extraMap.isEmpty() ? null : extraMap,
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(), "Failed to retrieve country/city info: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    public List<AutocompleteSuggestion> autocomplete(String input, Coordinates biasLocation, Integer radiusMeters, int limit) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getNominatim().getBaseUrl())
                    .path("/search")
                    .queryParam("q", input)
                    .queryParam("format", "jsonv2")
                    .queryParam("addressdetails", 1)
                    .queryParam("limit", Math.min(Math.max(1, limit), 20));

            if (biasLocation != null) {
                int radius = radiusMeters != null ? radiusMeters : 25000;
                BoundingBox box = GeoMathUtils.boundingBoxFromRadius(biasLocation.latitude(), biasLocation.longitude(), radius);
                // Nominatim viewbox=<x1>,<y1>,<x2>,<y2> (west, north, east, south)
                String viewbox = box.westLon() + "," + box.northLat() + "," + box.eastLon() + "," + box.southLat();
                builder.queryParam("viewbox", viewbox);
            }

            URI uri = builder.build().encode().toUri();
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            List<AutocompleteSuggestion> suggestions = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    String displayName = node.path("display_name").asText("");
                    String name = node.path("name").asText("");
                    if (name.isBlank() && displayName.contains(",")) {
                        name = displayName.substring(0, displayName.indexOf(',')).trim();
                    } else if (name.isBlank()) {
                        name = displayName;
                    }
                    String secondary = displayName.startsWith(name + ",")
                            ? displayName.substring(name.length() + 1).trim()
                            : displayName;

                    String osmType = node.path("osm_type").asText("node");
                    long osmId = node.path("osm_id").asLong(node.path("place_id").asLong());
                    String placeId = "osm:" + osmType + ":" + osmId;

                    double lat = node.path("lat").asDouble();
                    double lon = node.path("lon").asDouble();

                    List<String> types = new ArrayList<>();
                    if (node.hasNonNull("category")) {
                        types.add(node.get("category").asText());
                    }
                    if (node.hasNonNull("type")) {
                        types.add(node.get("type").asText());
                    }

                    suggestions.add(new AutocompleteSuggestion(
                            placeId,
                            displayName,
                            name,
                            secondary,
                            types,
                            new Coordinates(lat, lon),
                            getProviderName()
                    ));
                }
            }
            return suggestions;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(), "Failed location autocomplete: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    private GeocodeResult parseGeocodeNode(JsonNode node) {
        double lat = node.path("lat").asDouble();
        double lon = node.path("lon").asDouble();
        String displayName = node.path("display_name").asText("");
        String osmType = node.path("osm_type").asText("node");
        long osmId = node.path("osm_id").asLong(node.path("place_id").asLong());
        String placeId = "osm:" + osmType + ":" + osmId;

        JsonNode addrNode = node.path("address");
        String city = firstNonBlank(addrNode, "city", "town", "village", "municipality", "suburb");
        String state = firstNonBlank(addrNode, "state", "region", "province");
        String country = addrNode.path("country").asText(null);
        String countryCode = addrNode.path("country_code").asText(null);
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase();
        }
        String postcode = addrNode.path("postcode").asText(null);
        String street = firstNonBlank(addrNode, "road", "pedestrian", "street");
        String houseNumber = addrNode.path("house_number").asText(null);
        String neighborhood = firstNonBlank(addrNode, "neighbourhood", "suburb", "quarter");
        String district = firstNonBlank(addrNode, "state_district", "county", "city_district");

        AddressDetails details = new AddressDetails(
                displayName,
                houseNumber,
                street,
                neighborhood,
                city,
                district,
                state,
                country,
                countryCode,
                postcode
        );

        BoundingBox bbox = null;
        JsonNode bboxNode = node.path("boundingbox");
        if (bboxNode.isArray() && bboxNode.size() == 4) {
            double south = Double.parseDouble(bboxNode.get(0).asText());
            double north = Double.parseDouble(bboxNode.get(1).asText());
            double west = Double.parseDouble(bboxNode.get(2).asText());
            double east = Double.parseDouble(bboxNode.get(3).asText());
            bbox = new BoundingBox(south, west, north, east);
        }

        return new GeocodeResult(
                placeId,
                displayName,
                new Coordinates(lat, lon),
                details,
                bbox,
                node.path("type").asText("place"),
                node.path("category").asText(null),
                getProviderName()
        );
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                String val = node.get(field).asText();
                if (val != null && !val.isBlank()) {
                    return val;
                }
            }
        }
        return null;
    }
}
