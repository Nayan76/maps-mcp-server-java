package com.example.mapsmcp.providers.google;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.enums.PlaceCategory;
import com.example.mapsmcp.enums.ProviderType;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.models.AddressDetails;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.BoundingBox;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.DistanceMatrixElement;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.models.RouteOption;
import com.example.mapsmcp.models.RouteStep;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.models.TrafficIncident;
import com.example.mapsmcp.providers.DirectionsProvider;
import com.example.mapsmcp.providers.DistanceMatrixProvider;
import com.example.mapsmcp.providers.ElevationProvider;
import com.example.mapsmcp.providers.GeocodingProvider;
import com.example.mapsmcp.providers.PlacesProvider;
import com.example.mapsmcp.providers.TrafficProvider;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class GoogleMapsProvider implements
        PlacesProvider,
        GeocodingProvider,
        DirectionsProvider,
        DistanceMatrixProvider,
        ElevationProvider,
        TrafficProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsProvider.class);

    private final RestClient restClient;
    private final MapsProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleMapsProvider(RestClient.Builder restClientBuilder,
                              MapsProperties properties,
                              ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE;
    }

    @Override
    public String getProviderName() {
        return "Google Maps Platform";
    }

    @Override
    public boolean isConfigured() {
        return properties.getGoogle().hasApiKey();
    }

    private void ensureApiKeyConfigured() {
        if (!isConfigured()) {
            throw new ProviderApiException(getProviderName(), 401,
                    "GOOGLE_MAPS_API_KEY is not configured. Set GOOGLE_MAPS_API_KEY or switch MAP_PROVIDER to openstreetmap.");
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public List<PlaceSummary> searchPlaces(SearchPlacesRequest request) {
        ensureApiKeyConfigured();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/place/textsearch/json")
                    .queryParam("query", request.query())
                    .queryParam("key", properties.getGoogle().getApiKey());

            if (request.latitude() != null && request.longitude() != null) {
                builder.queryParam("location", request.latitude() + "," + request.longitude());
                if (request.radiusMeters() != null) {
                    builder.queryParam("radius", request.radiusMeters());
                }
            }
            if (request.category() != null && !request.category().isBlank()) {
                PlaceCategory pc = PlaceCategory.fromInput(request.category());
                builder.queryParam("type", pc != null ? pc.getGoogleType() : request.category().trim().toLowerCase());
            }
            if (Boolean.TRUE.equals(request.openNow())) {
                builder.queryParam("opennow", "true");
            }

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            Coordinates refCenter = (request.latitude() != null && request.longitude() != null)
                    ? new Coordinates(request.latitude(), request.longitude())
                    : null;

            return parsePlacesList(root.path("results"), refCenter, request.minRating(),
                    request.limit() != null ? request.limit() : 10);
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Places text search failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public List<PlaceSummary> searchNearby(SearchNearbyRequest request) {
        ensureApiKeyConfigured();
        try {
            int radius = request.radiusMeters() != null ? request.radiusMeters() : 5000;
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/place/nearbysearch/json")
                    .queryParam("location", request.latitude() + "," + request.longitude())
                    .queryParam("radius", radius)
                    .queryParam("key", properties.getGoogle().getApiKey());

            if (request.category() != null && !request.category().isBlank()) {
                PlaceCategory pc = PlaceCategory.fromInput(request.category());
                builder.queryParam("type", pc != null ? pc.getGoogleType() : request.category().trim().toLowerCase());
            }
            if (request.keyword() != null && !request.keyword().isBlank()) {
                builder.queryParam("keyword", request.keyword());
            }
            if (Boolean.TRUE.equals(request.openNow())) {
                builder.queryParam("opennow", "true");
            }

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            Coordinates refCenter = new Coordinates(request.latitude(), request.longitude());

            return parsePlacesList(root.path("results"), refCenter, request.minRating(),
                    request.limit() != null ? request.limit() : 10);
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Places nearby search failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public PlaceDetails getPlaceDetails(String placeId) {
        ensureApiKeyConfigured();
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/place/details/json")
                    .queryParam("place_id", placeId)
                    .queryParam("fields", "place_id,name,formatted_address,geometry,types,formatted_phone_number,international_phone_number,website,url,rating,user_ratings_total,price_level,business_status,opening_hours,photos,reviews,address_components")
                    .queryParam("key", properties.getGoogle().getApiKey())
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = executeGetAndVerifyStatus(uri);
            JsonNode r = root.path("result");
            if (r.isMissingNode() || r.isNull()) {
                throw new ProviderApiException(getProviderName(), 404, "Place details not found for ID: " + placeId);
            }

            JsonNode loc = r.path("geometry").path("location");
            Coordinates coords = new Coordinates(loc.path("lat").asDouble(), loc.path("lng").asDouble());

            BoundingBox viewport = null;
            JsonNode vp = r.path("geometry").path("viewport");
            if (!vp.isMissingNode() && vp.has("southwest") && vp.has("northeast")) {
                viewport = new BoundingBox(
                        vp.path("southwest").path("lat").asDouble(),
                        vp.path("southwest").path("lng").asDouble(),
                        vp.path("northeast").path("lat").asDouble(),
                        vp.path("northeast").path("lng").asDouble()
                );
            }

            List<String> types = new ArrayList<>();
            if (r.path("types").isArray()) {
                for (JsonNode t : r.path("types")) {
                    types.add(t.asText());
                }
            }

            String postalCode = extractAddressComponent(r.path("address_components"), "postal_code");

            JsonNode ohNode = r.path("opening_hours");
            Boolean openNow = ohNode.hasNonNull("open_now") ? ohNode.get("open_now").asBoolean() : null;
            List<String> weekdayText = new ArrayList<>();
            if (ohNode.path("weekday_text").isArray()) {
                for (JsonNode wt : ohNode.path("weekday_text")) {
                    weekdayText.add(wt.asText());
                }
            }
            OpeningHoursInfo hoursInfo = new OpeningHoursInfo(
                    placeId,
                    r.path("name").asText(null),
                    openNow,
                    weekdayText,
                    weekdayText.isEmpty() ? null : String.join("; ", weekdayText),
                    !ohNode.isMissingNode() && !ohNode.isNull(),
                    !ohNode.isMissingNode() ? "Verified opening hours from Google Places." : "Opening hours not available for this place.",
                    getProviderName()
            );

            List<PlacePhoto> photos = new ArrayList<>();
            if (r.path("photos").isArray()) {
                for (JsonNode p : r.path("photos")) {
                    String ref = p.path("photo_reference").asText(null);
                    int width = p.path("width").asInt(800);
                    int height = p.path("height").asInt(600);
                    List<String> attrs = new ArrayList<>();
                    if (p.path("html_attributions").isArray()) {
                        for (JsonNode a : p.path("html_attributions")) {
                            attrs.add(a.asText());
                        }
                    }
                    String photoEndpoint = properties.getGoogle().getBaseUrl()
                            + "/maps/api/place/photo?maxwidth=" + Math.min(width, 800)
                            + "&photo_reference=" + ref;
                    photos.add(new PlacePhoto(ref, width, height, photoEndpoint, attrs, getProviderName()));
                }
            }

            List<PlaceReview> reviews = new ArrayList<>();
            if (r.path("reviews").isArray()) {
                for (JsonNode rev : r.path("reviews")) {
                    reviews.add(new PlaceReview(
                            rev.path("author_name").asText(null),
                            rev.path("author_url").asText(null),
                            rev.hasNonNull("rating") ? rev.get("rating").asDouble() : null,
                            rev.path("relative_time_description").asText(null),
                            rev.path("text").asText(null),
                            rev.hasNonNull("time") ? rev.get("time").asLong() : null,
                            rev.path("language").asText(null)
                    ));
                }
            }

            return new PlaceDetails(
                    r.path("place_id").asText(placeId),
                    r.path("name").asText(null),
                    r.path("formatted_address").asText(null),
                    postalCode,
                    coords,
                    viewport,
                    types,
                    r.path("formatted_phone_number").asText(null),
                    r.path("international_phone_number").asText(null),
                    r.path("website").asText(null),
                    r.path("url").asText(null),
                    r.hasNonNull("rating") ? r.get("rating").asDouble() : null,
                    r.hasNonNull("user_ratings_total") ? r.get("user_ratings_total").asInt() : null,
                    r.hasNonNull("price_level") ? r.get("price_level").asInt() : null,
                    r.path("business_status").asText(null),
                    hoursInfo,
                    photos,
                    reviews,
                    null,
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Failed to retrieve Google Place details: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public List<AutocompleteSuggestion> autocompleteLocation(String input, Coordinates biasLocation, Integer radiusMeters, int limit) {
        ensureApiKeyConfigured();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/place/autocomplete/json")
                    .queryParam("input", input)
                    .queryParam("key", properties.getGoogle().getApiKey());

            if (biasLocation != null) {
                builder.queryParam("location", biasLocation.latitude() + "," + biasLocation.longitude());
                builder.queryParam("radius", radiusMeters != null ? radiusMeters : 25000);
            }

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            List<AutocompleteSuggestion> suggestions = new ArrayList<>();
            JsonNode predictions = root.path("predictions");
            if (predictions.isArray()) {
                for (JsonNode p : predictions) {
                    if (suggestions.size() >= limit) break;
                    List<String> types = new ArrayList<>();
                    if (p.path("types").isArray()) {
                        for (JsonNode t : p.path("types")) {
                            types.add(t.asText());
                        }
                    }
                    JsonNode fmt = p.path("structured_formatting");
                    suggestions.add(new AutocompleteSuggestion(
                            p.path("place_id").asText(null),
                            p.path("description").asText(null),
                            fmt.path("main_text").asText(null),
                            fmt.path("secondary_text").asText(null),
                            types,
                            null,
                            getProviderName()
                    ));
                }
            }
            return suggestions;
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Places autocomplete failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public List<PlacePhoto> getPlacePhotos(String placeId, Integer maxWidth) {
        PlaceDetails details = getPlaceDetails(placeId);
        return details.photos() != null ? details.photos() : List.of();
    }

    @Override
    public List<PlaceReview> getPlaceReviews(String placeId) {
        PlaceDetails details = getPlaceDetails(placeId);
        return details.reviews() != null ? details.reviews() : List.of();
    }

    @Override
    public OpeningHoursInfo getPlaceOpeningHours(String placeId) {
        PlaceDetails details = getPlaceDetails(placeId);
        return details.openingHours();
    }

    @Override
    @Retry(name = "providerRetry")
    public List<GeocodeResult> geocodeAddress(String address, String countryCode, int limit) {
        ensureApiKeyConfigured();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/geocode/json")
                    .queryParam("address", address)
                    .queryParam("key", properties.getGoogle().getApiKey());

            if (countryCode != null && !countryCode.isBlank()) {
                builder.queryParam("components", "country:" + countryCode.trim().toUpperCase());
            }

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            List<GeocodeResult> list = new ArrayList<>();
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode r : results) {
                    if (list.size() >= limit) break;
                    list.add(parseGoogleGeocodeNode(r));
                }
            }
            return list;
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Geocoding failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public GeocodeResult reverseGeocode(Coordinates coordinates) {
        ensureApiKeyConfigured();
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/geocode/json")
                    .queryParam("latlng", coordinates.latitude() + "," + coordinates.longitude())
                    .queryParam("key", properties.getGoogle().getApiKey())
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = executeGetAndVerifyStatus(uri);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No reverse geocode results found.");
            }
            return parseGoogleGeocodeNode(results.get(0));
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Reverse Geocoding failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public CountryCityInfo getCountryAndCityInfo(String query, String countryCode) {
        List<GeocodeResult> results = geocodeAddress(query, countryCode, 1);
        if (results.isEmpty()) {
            throw new ProviderApiException(getProviderName(), 404, "No city or country found for query: " + query);
        }
        GeocodeResult gr = results.getFirst();
        AddressDetails addr = gr.addressDetails();
        String tzId = null;
        try {
            TimezoneInfo tz = getTimezone(gr.coordinates(), null);
            tzId = tz.timeZoneId();
        } catch (Exception ignored) {
        }
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
                tzId,
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
        ensureApiKeyConfigured();
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/directions/json")
                    .queryParam("origin", request.origin())
                    .queryParam("destination", request.destination())
                    .queryParam("mode", mode == TravelMode.CYCLING ? "bicycling" : mode.getValue())
                    .queryParam("alternatives", request.alternatives())
                    .queryParam("departure_time", "now")
                    .queryParam("key", properties.getGoogle().getApiKey());

            if (request.waypoints() != null && !request.waypoints().isEmpty()) {
                builder.queryParam("waypoints", String.join("|", request.waypoints()));
            }

            List<String> avoidList = new ArrayList<>();
            if (request.avoidTolls()) avoidList.add("tolls");
            if (request.avoidHighways()) avoidList.add("highways");
            if (request.avoidFerries()) avoidList.add("ferries");
            if (!avoidList.isEmpty()) {
                builder.queryParam("avoid", String.join("|", avoidList));
            }

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            JsonNode routesNode = root.path("routes");
            List<RouteOption> routes = new ArrayList<>();
            if (routesNode.isArray()) {
                int idx = 0;
                for (JsonNode rNode : routesNode) {
                    routes.add(parseGoogleRoute(rNode, idx++, mode));
                }
            }
            if (routes.isEmpty()) {
                throw new ProviderApiException(getProviderName(), 404, "No routes found.");
            }
            return new DirectionsResponse(
                    request.origin(),
                    request.destination(),
                    mode.getValue(),
                    routes.size(),
                    routes.getFirst(),
                    routes.size() > 1 ? routes.subList(1, routes.size()) : List.of(),
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Directions API failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
        ensureApiKeyConfigured();
        TravelMode mode = request.travelMode() != null ? request.travelMode() : TravelMode.DRIVING;
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/distancematrix/json")
                    .queryParam("origins", String.join("|", request.origins()))
                    .queryParam("destinations", String.join("|", request.destinations()))
                    .queryParam("mode", mode == TravelMode.CYCLING ? "bicycling" : mode.getValue())
                    .queryParam("departure_time", "now")
                    .queryParam("key", properties.getGoogle().getApiKey());

            JsonNode root = executeGetAndVerifyStatus(builder.build().encode().toUri());
            List<String> originAddresses = new ArrayList<>();
            if (root.path("origin_addresses").isArray()) {
                root.path("origin_addresses").forEach(n -> originAddresses.add(n.asText()));
            }
            List<String> destAddresses = new ArrayList<>();
            if (root.path("destination_addresses").isArray()) {
                root.path("destination_addresses").forEach(n -> destAddresses.add(n.asText()));
            }

            List<DistanceMatrixElement> elements = new ArrayList<>();
            JsonNode rows = root.path("rows");
            if (rows.isArray()) {
                for (int i = 0; i < rows.size(); i++) {
                    JsonNode rowElements = rows.get(i).path("elements");
                    if (rowElements.isArray()) {
                        for (int j = 0; j < rowElements.size(); j++) {
                            JsonNode el = rowElements.get(j);
                            String status = el.path("status").asText("UNKNOWN");
                            Double distMeters = el.has("distance") ? el.path("distance").path("value").asDouble() : null;
                            String distText = el.has("distance") ? el.path("distance").path("text").asText(null) : null;
                            Long durSec = el.has("duration") ? el.path("duration").path("value").asLong() : null;
                            String durText = el.has("duration") ? el.path("duration").path("text").asText(null) : null;
                            Long trafficSec = el.has("duration_in_traffic")
                                    ? el.path("duration_in_traffic").path("value").asLong()
                                    : null;

                            elements.add(new DistanceMatrixElement(
                                    i, j,
                                    i < originAddresses.size() ? originAddresses.get(i) : request.origins().get(i),
                                    j < destAddresses.size() ? destAddresses.get(j) : request.destinations().get(j),
                                    status,
                                    distMeters,
                                    distText,
                                    durSec,
                                    durText,
                                    trafficSec
                            ));
                        }
                    }
                }
            }

            return new DistanceMatrixResponse(
                    originAddresses.isEmpty() ? request.origins() : originAddresses,
                    destAddresses.isEmpty() ? request.destinations() : destAddresses,
                    mode.getValue(),
                    elements,
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Distance Matrix API failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public List<ElevationPoint> getElevation(List<Coordinates> locations) {
        ensureApiKeyConfigured();
        try {
            String locs = locations.stream()
                    .map(c -> String.format(Locale.US, "%.6f,%.6f", c.latitude(), c.longitude()))
                    .collect(Collectors.joining("|"));

            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/elevation/json")
                    .queryParam("locations", locs)
                    .queryParam("key", properties.getGoogle().getApiKey())
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = executeGetAndVerifyStatus(uri);
            List<ElevationPoint> points = new ArrayList<>();
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode r : results) {
                    JsonNode loc = r.path("location");
                    points.add(new ElevationPoint(
                            new Coordinates(loc.path("lat").asDouble(), loc.path("lng").asDouble()),
                            r.path("elevation").asDouble(),
                            r.hasNonNull("resolution") ? r.get("resolution").asDouble() : null,
                            getProviderName()
                    ));
                }
            }
            return points;
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Elevation API failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    @Retry(name = "providerRetry")
    public TimezoneInfo getTimezone(Coordinates coordinates, Long timestampSeconds) {
        ensureApiKeyConfigured();
        try {
            long ts = timestampSeconds != null ? timestampSeconds : Instant.now().getEpochSecond();
            URI uri = UriComponentsBuilder
                    .fromUriString(properties.getGoogle().getBaseUrl())
                    .path("/maps/api/timezone/json")
                    .queryParam("location", coordinates.latitude() + "," + coordinates.longitude())
                    .queryParam("timestamp", ts)
                    .queryParam("key", properties.getGoogle().getApiKey())
                    .build()
                    .encode()
                    .toUri();

            JsonNode root = executeGetAndVerifyStatus(uri);
            String tzId = root.path("timeZoneId").asText("UTC");
            String tzName = root.path("timeZoneName").asText("UTC");
            int rawOffset = root.path("rawOffset").asInt(0);
            int dstOffset = root.path("dstOffset").asInt(0);

            ZonedDateTime zdt = Instant.ofEpochSecond(ts).atZone(ZoneId.of(tzId));
            return new TimezoneInfo(
                    coordinates,
                    tzId,
                    tzName,
                    rawOffset,
                    dstOffset,
                    zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    getProviderName()
            );
        } catch (ProviderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderApiException(getProviderName(),
                    "Google Time Zone API failed: " + SecretMasker.maskUrlSecrets(e.getMessage()), e);
        }
    }

    @Override
    public TrafficConditionsResponse getTrafficConditions(Coordinates center, Integer radiusMeters, String origin, String destination) {
        ensureApiKeyConfigured();
        String routeOrigin = origin;
        String routeDest = destination;
        if ((routeOrigin == null || routeOrigin.isBlank() || routeDest == null || routeDest.isBlank()) && center != null) {
            int r = radiusMeters != null ? radiusMeters : 3000;
            BoundingBox bbox = GeoMathUtils.boundingBoxFromRadius(center.latitude(), center.longitude(), r);
            routeOrigin = bbox.southLat() + "," + bbox.westLon();
            routeDest = bbox.northLat() + "," + bbox.eastLon();
        }

        DirectionsRequest req = new DirectionsRequest(
                routeOrigin, routeDest, TravelMode.DRIVING, List.of(), false, false, false, false
        );
        DirectionsResponse resp = getDirections(req);
        RouteOption primary = resp.primaryRoute();

        long normalSec = primary.totalDurationSeconds();
        Long trafficSec = primary.durationInTrafficSeconds() != null ? primary.durationInTrafficSeconds() : normalSec;
        double ratio = normalSec > 0 ? GeoMathUtils.round2((double) trafficSec / normalSec) : 1.0;

        String congestion;
        if (ratio >= 1.5) {
            congestion = "SEVERE";
        } else if (ratio >= 1.25) {
            congestion = "HEAVY";
        } else if (ratio >= 1.1) {
            congestion = "MODERATE";
        } else {
            congestion = "LOW_OR_FREE_FLOW";
        }

        List<TrafficIncident> incidents = new ArrayList<>();
        if (primary.warnings() != null) {
            for (String w : primary.warnings()) {
                incidents.add(new TrafficIncident(null, "ROUTE_WARNING", "INFO", w, primary.summary(), center, null, null, false));
            }
        }

        return new TrafficConditionsResponse(
                true,
                routeOrigin + " -> " + routeDest,
                center != null ? center : primary.startLocation(),
                congestion,
                normalSec,
                trafficSec,
                ratio,
                incidents,
                getProviderName(),
                "Live traffic delay computed from Google Directions departure_time=now duration_in_traffic telemetry."
        );
    }

    private JsonNode executeGetAndVerifyStatus(URI uri) throws Exception {
        String body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(body);
        String status = root.path("status").asText("OK");
        if ("OK".equalsIgnoreCase(status) || "ZERO_RESULTS".equalsIgnoreCase(status)) {
            return root;
        }
        String errMsg = root.path("error_message").asText("Google Maps API returned status: " + status);
        throw new ProviderApiException(getProviderName(), 400, status + " - " + errMsg);
    }

    private List<PlaceSummary> parsePlacesList(JsonNode results, Coordinates refCenter, Double minRating, int limit) {
        List<PlaceSummary> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode r : results) {
                Double rating = r.hasNonNull("rating") ? r.get("rating").asDouble() : null;
                if (minRating != null && (rating == null || rating < minRating)) {
                    continue;
                }
                JsonNode loc = r.path("geometry").path("location");
                Coordinates coords = new Coordinates(loc.path("lat").asDouble(), loc.path("lng").asDouble());
                Double dist = refCenter != null ? GeoMathUtils.haversineDistanceMeters(refCenter, coords) : null;

                List<String> types = new ArrayList<>();
                if (r.path("types").isArray()) {
                    for (JsonNode t : r.path("types")) {
                        types.add(t.asText());
                    }
                }

                Boolean openNow = r.path("opening_hours").hasNonNull("open_now")
                        ? r.path("opening_hours").get("open_now").asBoolean()
                        : null;

                String address = r.hasNonNull("formatted_address")
                        ? r.get("formatted_address").asText()
                        : r.path("vicinity").asText(null);

                list.add(new PlaceSummary(
                        r.path("place_id").asText(null),
                        r.path("name").asText(null),
                        address,
                        null,
                        coords,
                        types,
                        rating,
                        r.hasNonNull("user_ratings_total") ? r.get("user_ratings_total").asInt() : null,
                        r.hasNonNull("price_level") ? r.get("price_level").asInt() : null,
                        openNow,
                        dist,
                        r.path("business_status").asText(null),
                        null,
                        null,
                        getProviderName(),
                        null
                ));
            }
        }
        if (refCenter != null) {
            list.sort(Comparator.comparing(p -> p.distanceMeters() != null ? p.distanceMeters() : Double.MAX_VALUE));
        }
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    private GeocodeResult parseGoogleGeocodeNode(JsonNode r) {
        JsonNode loc = r.path("geometry").path("location");
        Coordinates coords = new Coordinates(loc.path("lat").asDouble(), loc.path("lng").asDouble());
        JsonNode comps = r.path("address_components");

        AddressDetails details = new AddressDetails(
                r.path("formatted_address").asText(null),
                extractAddressComponent(comps, "street_number"),
                extractAddressComponent(comps, "route"),
                extractAddressComponent(comps, "neighborhood", "sublocality"),
                extractAddressComponent(comps, "locality", "postal_town"),
                extractAddressComponent(comps, "administrative_area_level_2"),
                extractAddressComponent(comps, "administrative_area_level_1"),
                extractAddressComponent(comps, "country"),
                extractAddressComponentShort(comps, "country"),
                extractAddressComponent(comps, "postal_code")
        );

        BoundingBox bbox = null;
        JsonNode vp = r.path("geometry").path("viewport");
        if (!vp.isMissingNode() && vp.has("southwest") && vp.has("northeast")) {
            bbox = new BoundingBox(
                    vp.path("southwest").path("lat").asDouble(),
                    vp.path("southwest").path("lng").asDouble(),
                    vp.path("northeast").path("lat").asDouble(),
                    vp.path("northeast").path("lng").asDouble()
            );
        }

        String primaryType = r.path("types").isArray() && !r.path("types").isEmpty()
                ? r.path("types").get(0).asText()
                : "address";

        return new GeocodeResult(
                r.path("place_id").asText(null),
                r.path("formatted_address").asText(null),
                coords,
                details,
                bbox,
                r.path("geometry").path("location_type").asText("APPROXIMATE"),
                primaryType,
                getProviderName()
        );
    }

    private RouteOption parseGoogleRoute(JsonNode rNode, int index, TravelMode mode) {
        String summary = rNode.path("summary").asText("Route " + (index + 1));
        String overviewPolyline = rNode.path("overview_polyline").path("points").asText(null);
        List<Coordinates> geom = GeoMathUtils.decodePolyline(overviewPolyline);

        BoundingBox bounds = null;
        JsonNode bNode = rNode.path("bounds");
        if (bNode.has("southwest") && bNode.has("northeast")) {
            bounds = new BoundingBox(
                    bNode.path("southwest").path("lat").asDouble(),
                    bNode.path("southwest").path("lng").asDouble(),
                    bNode.path("northeast").path("lat").asDouble(),
                    bNode.path("northeast").path("lng").asDouble()
            );
        }

        double totalDist = 0;
        long totalDur = 0;
        Long totalTrafficDur = null;
        Coordinates startLoc = null;
        String startAddr = null;
        Coordinates endLoc = null;
        String endAddr = null;
        List<RouteStep> steps = new ArrayList<>();

        JsonNode legs = rNode.path("legs");
        int stepNum = 1;
        if (legs.isArray()) {
            for (int i = 0; i < legs.size(); i++) {
                JsonNode leg = legs.get(i);
                totalDist += leg.path("distance").path("value").asDouble(0);
                totalDur += leg.path("duration").path("value").asLong(0);
                if (leg.has("duration_in_traffic")) {
                    totalTrafficDur = (totalTrafficDur == null ? 0 : totalTrafficDur)
                            + leg.path("duration_in_traffic").path("value").asLong(0);
                }
                if (i == 0) {
                    startAddr = leg.path("start_address").asText(null);
                    startLoc = new Coordinates(
                            leg.path("start_location").path("lat").asDouble(),
                            leg.path("start_location").path("lng").asDouble()
                    );
                }
                if (i == legs.size() - 1) {
                    endAddr = leg.path("end_address").asText(null);
                    endLoc = new Coordinates(
                            leg.path("end_location").path("lat").asDouble(),
                            leg.path("end_location").path("lng").asDouble()
                    );
                }
                JsonNode legSteps = leg.path("steps");
                if (legSteps.isArray()) {
                    for (JsonNode s : legSteps) {
                        double sDist = s.path("distance").path("value").asDouble(0);
                        long sDur = s.path("duration").path("value").asLong(0);
                        String rawHtml = s.path("html_instructions").asText("");
                        String cleanInstruction = rawHtml.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                        Coordinates sStart = new Coordinates(
                                s.path("start_location").path("lat").asDouble(),
                                s.path("start_location").path("lng").asDouble()
                        );
                        Coordinates sEnd = new Coordinates(
                                s.path("end_location").path("lat").asDouble(),
                                s.path("end_location").path("lng").asDouble()
                        );
                        steps.add(new RouteStep(
                                stepNum++,
                                cleanInstruction,
                                sDist,
                                s.path("distance").path("text").asText(GeoMathUtils.formatDistance(sDist)),
                                sDur,
                                s.path("duration").path("text").asText(GeoMathUtils.formatDuration(sDur)),
                                sStart,
                                sEnd,
                                s.path("maneuver").asText(null),
                                null,
                                s.path("travel_mode").asText(mode.getValue())
                        ));
                    }
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        if (rNode.path("warnings").isArray()) {
            rNode.path("warnings").forEach(w -> warnings.add(w.asText()));
        }

        return new RouteOption(
                index,
                summary,
                totalDist,
                GeoMathUtils.formatDistance(totalDist),
                totalDur,
                GeoMathUtils.formatDuration(totalDur),
                totalTrafficDur,
                totalTrafficDur != null ? GeoMathUtils.formatDuration(totalTrafficDur) : null,
                startLoc,
                startAddr,
                endLoc,
                endAddr,
                bounds,
                overviewPolyline,
                geom.size() > 200 ? geom.subList(0, 200) : geom,
                steps,
                warnings,
                null,
                List.of()
        );
    }

    private String extractAddressComponent(JsonNode components, String... targetTypes) {
        if (components == null || !components.isArray()) {
            return null;
        }
        for (String target : targetTypes) {
            for (JsonNode comp : components) {
                JsonNode types = comp.path("types");
                if (types.isArray()) {
                    for (JsonNode t : types) {
                        if (target.equalsIgnoreCase(t.asText())) {
                            return comp.path("long_name").asText(null);
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractAddressComponentShort(JsonNode components, String targetType) {
        if (components == null || !components.isArray()) {
            return null;
        }
        for (JsonNode comp : components) {
            JsonNode types = comp.path("types");
            if (types.isArray()) {
                for (JsonNode t : types) {
                    if (targetType.equalsIgnoreCase(t.asText())) {
                        return comp.path("short_name").asText(null);
                    }
                }
            }
        }
        return null;
    }
}
