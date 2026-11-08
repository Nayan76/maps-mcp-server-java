package com.example.mapsmcp.providers.openstreetmap;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.enums.PlaceCategory;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.providers.MapTileProvider;
import com.example.mapsmcp.providers.PlacesProvider;
import com.example.mapsmcp.providers.nominatim.NominatimGeocodingProvider;
import com.example.mapsmcp.utils.GeoMathUtils;
import com.example.mapsmcp.utils.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenStreetMapPlacesProvider implements PlacesProvider, MapTileProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenStreetMapPlacesProvider.class);

    private final RestClient restClient;
    private final MapsProperties properties;
    private final NominatimGeocodingProvider nominatimProvider;
    private final ObjectMapper objectMapper;

    public OpenStreetMapPlacesProvider(RestClient.Builder restClientBuilder,
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
        return ProviderType.OPENSTREETMAP;
    }

    @Override
    public String getProviderName() {
        return "OpenStreetMap (Overpass & Nominatim)";
    }

    @Override
    public boolean isConfigured() {
        return properties.getOverpass().getBaseUrl() != null && !properties.getOverpass().getBaseUrl().isBlank();
    }

    @Override
    @Retry(name = "providerRetry")
    public List<PlaceSummary> searchPlaces(SearchPlacesRequest request) {
        int limit = request.limit() != null ? request.limit() : 10;
        if (request.latitude() != null && request.longitude() != null && request.category() != null && !request.category().isBlank()) {
            SearchNearbyRequest nearbyReq = new SearchNearbyRequest(
                    request.latitude(),
                    request.longitude(),
                    request.radiusMeters() != null ? request.radiusMeters() : 5000,
                    request.category(),
                    request.query(),
                    request.openNow(),
                    request.minRating(),
                    limit
            );
            List<PlaceSummary> nearbyResults = searchNearby(nearbyReq);
            if (!nearbyResults.isEmpty()) {
                return nearbyResults;
            }
        }

        List<GeocodeResult> geocodeResults = nominatimProvider.geocodeAddress(request.query(), null, limit);
        List<PlaceSummary> summaries = new ArrayList<>();
        for (GeocodeResult gr : geocodeResults) {
            Double dist = null;
            if (request.latitude() != null && request.longitude() != null && gr.coordinates() != null) {
                dist = GeoMathUtils.haversineDistanceMeters(
                        request.latitude(), request.longitude(),
                        gr.coordinates().latitude(), gr.coordinates().longitude()
                );
            }
            List<String> categories = new ArrayList<>();
            if (gr.category() != null) {
                categories.add(gr.category());
            }
            if (gr.locationType() != null) {
                categories.add(gr.locationType());
            }
            String name = gr.formattedAddress() != null && gr.formattedAddress().contains(",")
                    ? gr.formattedAddress().substring(0, gr.formattedAddress().indexOf(',')).trim()
                    : gr.formattedAddress();

            summaries.add(new PlaceSummary(
                    gr.placeId(),
                    name,
                    gr.formattedAddress(),
                    gr.addressDetails() != null ? gr.addressDetails().postalCode() : null,
                    gr.coordinates(),
                    categories,
                    null, // Never fabricate ratings
                    null,
                    null,
                    null,
                    dist,
                    "OPERATIONAL",
                    null,
                    null,
                    getProviderName(),
                    null
            ));
        }
        return summaries;
    }

    @Override
    @Retry(name = "providerRetry")
    public List<PlaceSummary> searchNearby(SearchNearbyRequest request) {
        int radius = request.radiusMeters() != null ? request.radiusMeters() : 5000;
        int limit = request.limit() != null ? request.limit() : 10;
        double lat = request.latitude();
        double lon = request.longitude();

        PlaceCategory category = PlaceCategory.fromInput(
                request.category() != null ? request.category() : request.keyword()
        );

        String tagFilter;
        if (category != null) {
            tagFilter = String.format(Locale.US, "[\"%s\"=\"%s\"]", category.getOsmKey(), category.getOsmValue());
        } else if (request.category() != null && !request.category().isBlank()) {
            String safeCat = request.category().trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
            tagFilter = String.format(Locale.US, "[\"amenity\"=\"%s\"]", safeCat);
        } else {
            tagFilter = "[\"amenity\"]";
        }

        String overpassQuery = String.format(Locale.US,
                "[out:json][timeout:15];(node%s(around:%d,%.6f,%.6f);way%s(around:%d,%.6f,%.6f););out center %d;",
                tagFilter, radius, lat, lon,
                tagFilter, radius, lat, lon,
                Math.min(limit * 2, 60)
        );

        try {
            String formBody = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);
            String responseBody = restClient.post()
                    .uri(URI.create(properties.getOverpass().getBaseUrl()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode elements = root.path("elements");
            List<PlaceSummary> places = new ArrayList<>();

            if (elements.isArray()) {
                for (JsonNode el : elements) {
                    PlaceSummary summary = parseOverpassElement(el, new Coordinates(lat, lon));
                    if (summary != null) {
                        if (request.keyword() != null && !request.keyword().isBlank() && category != null) {
                            String kw = request.keyword().trim().toLowerCase();
                            boolean matchesName = summary.name() != null && summary.name().toLowerCase().contains(kw);
                            boolean matchesCat = summary.categories() != null && summary.categories().stream().anyMatch(c -> c.toLowerCase().contains(kw));
                            if (!matchesName && !matchesCat && !kw.equalsIgnoreCase(category.name())) {
                                continue;
                            }
                        }
                        places.add(summary);
                    }
                }
            }

            places.sort(Comparator.comparing(
                    p -> p.distanceMeters() != null ? p.distanceMeters() : Double.MAX_VALUE
            ));

            return places.size() > limit ? places.subList(0, limit) : places;
        } catch (Exception e) {
            log.error("Overpass API searchNearby failed: {}", SecretMasker.maskUrlSecrets(e.getMessage()));
            throw new ProviderApiException(getProviderName(), "Failed nearby places search via Overpass API: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public PlaceDetails getPlaceDetails(String placeId) {
        try {
            String osmType = "node";
            long osmId = 0;
            if (placeId.startsWith("osm:")) {
                String[] parts = placeId.split(":");
                if (parts.length == 3) {
                    osmType = parts[1];
                    osmId = Long.parseLong(parts[2]);
                }
            } else if (placeId.matches("\\d+")) {
                osmId = Long.parseLong(placeId);
            } else {
                throw new ProviderApiException(getProviderName(), 400,
                        "Invalid OpenStreetMap placeId format '" + placeId + "'. Expected 'osm:node:<id>' or numeric OSM ID.");
            }

            String overpassQuery = String.format(Locale.US,
                    "[out:json][timeout:10];%s(%d);out center tags;",
                    osmType, osmId
            );
            String formBody = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);
            String responseBody = restClient.post()
                    .uri(URI.create(properties.getOverpass().getBaseUrl()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode elements = root.path("elements");
            if (!elements.isArray() || elements.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "Place not found in OpenStreetMap for ID: " + placeId);
            }

            JsonNode el = elements.get(0);
            PlaceSummary summary = parseOverpassElement(el, null);
            JsonNode tags = el.path("tags");

            String openingHoursRaw = firstTag(tags, "opening_hours");
            OpeningHoursInfo hoursInfo = new OpeningHoursInfo(
                    placeId,
                    summary.name(),
                    null,
                    openingHoursRaw != null ? List.of(openingHoursRaw) : List.of(),
                    openingHoursRaw,
                    openingHoursRaw != null,
                    openingHoursRaw != null
                            ? "Opening hours retrieved from OpenStreetMap tags."
                            : "Opening hours tag ('opening_hours') is not recorded in OpenStreetMap for this place.",
                    getProviderName()
            );

            List<PlacePhoto> photos = extractOsmPhotos(tags);

            return new PlaceDetails(
                    summary.placeId(),
                    summary.name(),
                    summary.formattedAddress(),
                    summary.postalCode(),
                    summary.coordinates(),
                    null,
                    summary.categories(),
                    summary.phoneNumber(),
                    summary.phoneNumber(),
                    summary.website(),
                    "https://www.openstreetmap.org/" + osmType + "/" + osmId,
                    null, // OpenStreetMap does not store user star ratings
                    null,
                    null,
                    "OPERATIONAL",
                    hoursInfo,
                    photos,
                    List.of(),
                    summary.additionalTags(),
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(), "Failed to get place details: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public List<AutocompleteSuggestion> autocompleteLocation(String input, Coordinates biasLocation, Integer radiusMeters, int limit) {
        return nominatimProvider.autocomplete(input, biasLocation, radiusMeters, limit);
    }

    @Override
    public List<PlacePhoto> getPlacePhotos(String placeId, Integer maxWidth) {
        PlaceDetails details = getPlaceDetails(placeId);
        if (details.photos() != null && !details.photos().isEmpty()) {
            return details.photos();
        }
        throw new UnsupportedCapabilityException(
                getProviderName(),
                "get_place_photos",
                "No Wikimedia Commons or image tag is linked to this OpenStreetMap feature (" + placeId + "). Configure GOOGLE_MAPS_API_KEY with Google Places API for rich business photo galleries."
        );
    }

    @Override
    public List<PlaceReview> getPlaceReviews(String placeId) {
        throw new UnsupportedCapabilityException(
                getProviderName(),
                "get_place_reviews",
                "OpenStreetMap does not store customer reviews or star ratings. Configure MAP_PROVIDER=google with a valid GOOGLE_MAPS_API_KEY to retrieve verified business reviews."
        );
    }

    @Override
    public OpeningHoursInfo getPlaceOpeningHours(String placeId) {
        PlaceDetails details = getPlaceDetails(placeId);
        return details.openingHours();
    }

    @Override
    public MapTileData getMapTileData(Coordinates center, int zoomLevel) {
        return GeoMathUtils.computeTileData(
                center.latitude(),
                center.longitude(),
                zoomLevel,
                properties.getTiles().getOsmTileUrlTemplate(),
                getProviderName(),
                "© OpenStreetMap contributors (https://www.openstreetmap.org/copyright)"
        );
    }

    private PlaceSummary parseOverpassElement(JsonNode el, Coordinates referenceCenter) {
        double lat;
        double lon;
        if (el.hasNonNull("lat") && el.hasNonNull("lon")) {
            lat = el.get("lat").asDouble();
            lon = el.get("lon").asDouble();
        } else if (el.hasNonNull("center")) {
            lat = el.path("center").path("lat").asDouble();
            lon = el.path("center").path("lon").asDouble();
        } else {
            return null;
        }

        JsonNode tags = el.path("tags");
        String type = el.path("type").asText("node");
        long id = el.path("id").asLong();
        String placeId = "osm:" + type + ":" + id;

        String name = firstTag(tags, "name", "name:en", "brand", "operator");
        if (name == null || name.isBlank()) {
            String amenity = firstTag(tags, "amenity", "shop", "tourism", "leisure");
            name = amenity != null ? "Unnamed " + amenity : "OSM Feature " + id;
        }

        String street = firstTag(tags, "addr:street");
        String houseNumber = firstTag(tags, "addr:housenumber");
        String city = firstTag(tags, "addr:city", "addr:suburb", "addr:town");
        String state = firstTag(tags, "addr:state");
        String postcode = firstTag(tags, "addr:postcode");
        String fullAddr = firstTag(tags, "addr:full");

        StringBuilder formatted = new StringBuilder();
        if (fullAddr != null) {
            formatted.append(fullAddr);
        } else {
            if (houseNumber != null) formatted.append(houseNumber).append(" ");
            if (street != null) formatted.append(street);
            if (city != null) {
                if (!formatted.isEmpty()) formatted.append(", ");
                formatted.append(city);
            }
            if (state != null) {
                if (!formatted.isEmpty()) formatted.append(", ");
                formatted.append(state);
            }
            if (postcode != null) {
                if (!formatted.isEmpty()) formatted.append(" ");
                formatted.append(postcode);
            }
        }
        String addressStr = formatted.isEmpty() ? null : formatted.toString().trim();

        List<String> categories = new ArrayList<>();
        for (String key : List.of("amenity", "tourism", "shop", "leisure", "healthcare", "Aeroway", "railway", "cuisine")) {
            String val = firstTag(tags, key);
            if (val != null) {
                categories.add(val);
            }
        }

        String phone = firstTag(tags, "phone", "contact:phone", "contact:mobile");
        String website = firstTag(tags, "website", "contact:website", "url");

        Coordinates coords = new Coordinates(lat, lon);
        Double distance = referenceCenter != null
                ? GeoMathUtils.haversineDistanceMeters(referenceCenter, coords)
                : null;

        Map<String, String> additionalTags = new LinkedHashMap<>();
        if (tags.isObject()) {
            tags.fields().forEachRemaining(e -> additionalTags.put(e.getKey(), e.getValue().asText()));
        }

        return new PlaceSummary(
                placeId,
                name,
                addressStr,
                postcode,
                coords,
                categories,
                null, // Never invent ratings
                null,
                null,
                null,
                distance,
                "OPERATIONAL",
                phone,
                website,
                getProviderName(),
                additionalTags.isEmpty() ? null : additionalTags
        );
    }

    private List<PlacePhoto> extractOsmPhotos(JsonNode tags) {
        List<PlacePhoto> photos = new ArrayList<>();
        String image = firstTag(tags, "image");
        if (image != null && (image.startsWith("http://") || image.startsWith("https://"))) {
            photos.add(new PlacePhoto("osm-image", null, null, image, List.of("OpenStreetMap image tag"), getProviderName()));
        }
        String wikimedia = firstTag(tags, "wikimedia_commons");
        if (wikimedia != null && !wikimedia.isBlank()) {
            String url = wikimedia.startsWith("http")
                    ? wikimedia
                    : "https://commons.wikimedia.org/wiki/" + wikimedia.replace(" ", "_");
            photos.add(new PlacePhoto(wikimedia, null, null, url, List.of("Wikimedia Commons"), getProviderName()));
        }
        return photos;
    }

    private String firstTag(JsonNode tags, String... keys) {
        if (tags == null || tags.isMissingNode()) {
            return null;
        }
        for (String key : keys) {
            if (tags.hasNonNull(key)) {
                String v = tags.get(key).asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }
}
