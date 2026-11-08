package com.example.mapsmcp.providers;

import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.enums.TravelMode;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.providers.google.GoogleMapsProvider;
import com.example.mapsmcp.providers.nominatim.NominatimGeocodingProvider;
import com.example.mapsmcp.providers.openstreetmap.OpenMeteoElevationTimezoneProvider;
import com.example.mapsmcp.providers.openstreetmap.OpenStreetMapPlacesProvider;
import com.example.mapsmcp.providers.osrm.OsrmRoutingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderWireMockIntegrationTest {

    private static WireMockServer wireMockServer;
    private MapsProperties properties;
    private ObjectMapper objectMapper;
    private NominatimGeocodingProvider nominatimProvider;
    private OpenStreetMapPlacesProvider osmPlacesProvider;
    private OsrmRoutingProvider osrmRoutingProvider;
    private GoogleMapsProvider googleMapsProvider;
    private OpenMeteoElevationTimezoneProvider elevationTimezoneProvider;

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        String baseUrl = wireMockServer.baseUrl();

        properties = new MapsProperties();
        properties.setAllowLocalhostUrls(true);
        properties.getNominatim().setBaseUrl(baseUrl);
        properties.getOverpass().setBaseUrl(baseUrl + "/api/interpreter");
        properties.getOsrm().setBaseUrl(baseUrl);
        properties.getGoogle().setBaseUrl(baseUrl);
        properties.getGoogle().setApiKey("test-google-api-key");
        properties.getOpenElevation().setBaseUrl(baseUrl);

        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();

        nominatimProvider = new NominatimGeocodingProvider(builder, properties, objectMapper);
        osmPlacesProvider = new OpenStreetMapPlacesProvider(builder, properties, nominatimProvider, objectMapper);
        osrmRoutingProvider = new OsrmRoutingProvider(builder, properties, nominatimProvider, objectMapper);
        googleMapsProvider = new GoogleMapsProvider(builder, properties, objectMapper);
        elevationTimezoneProvider = new OpenMeteoElevationTimezoneProvider(builder, properties, objectMapper);
    }

    @Test
    void shouldGeocodeAndReverseGeocodeWithNominatim() {
        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "place_id": 1001,
                                    "osm_type": "way",
                                    "osm_id": 554433,
                                    "lat": "22.544808",
                                    "lon": "88.342558",
                                    "display_name": "Victoria Memorial, 1, Queens Way, Kolkata, West Bengal, 700071, India",
                                    "type": "museum",
                                    "category": "tourism",
                                    "boundingbox": ["22.5430", "22.5460", "88.3410", "88.3440"],
                                    "address": {
                                      "road": "Queens Way",
                                      "city": "Kolkata",
                                      "state": "West Bengal",
                                      "postcode": "700071",
                                      "country": "India",
                                      "country_code": "in"
                                    },
                                    "extratags": {
                                      "population": "4496694",
                                      "wikidata": "Q1348"
                                    }
                                  }
                                ]
                                """)));

        List<GeocodeResult> results = nominatimProvider.geocodeAddress("Victoria Memorial", "IN", 5);
        assertThat(results).hasSize(1);
        GeocodeResult first = results.getFirst();
        assertThat(first.placeId()).isEqualTo("osm:way:554433");
        assertThat(first.coordinates().latitude()).isEqualTo(22.544808);
        assertThat(first.addressDetails().city()).isEqualTo("Kolkata");
        assertThat(first.addressDetails().postalCode()).isEqualTo("700071");

        CountryCityInfo cityInfo = nominatimProvider.getCountryAndCityInfo("Kolkata", "IN");
        assertThat(cityInfo.population()).isEqualTo(4496694L);
        assertThat(cityInfo.wikidataId()).isEqualTo("Q1348");
    }

    @Test
    void shouldSearchNearbyPlacesWithOverpassAndNeverInventRatings() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/interpreter"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "elements": [
                                    {
                                      "type": "node",
                                      "id": 987654,
                                      "lat": 22.5510,
                                      "lon": 88.3520,
                                      "tags": {
                                        "name": "Flurys",
                                        "amenity": "cafe",
                                        "addr:street": "Park Street",
                                        "addr:city": "Kolkata",
                                        "addr:postcode": "700016",
                                        "phone": "+91 33 4000 7453",
                                        "website": "https://flurys.com",
                                        "opening_hours": "Mo-Su 07:30-22:00"
                                      }
                                    }
                                  ]
                                }
                                """)));

        List<PlaceSummary> nearby = osmPlacesProvider.searchNearby(
                new SearchNearbyRequest(22.5500, 88.3500, 2000, "cafe", null, null, null, 5)
        );
        assertThat(nearby).hasSize(1);
        PlaceSummary cafe = nearby.getFirst();
        assertThat(cafe.name()).isEqualTo("Flurys");
        assertThat(cafe.rating()).isNull(); // Must never fabricate ratings
        assertThat(cafe.phoneNumber()).isEqualTo("+91 33 4000 7453");
        assertThat(cafe.distanceMeters()).isNotNull().isLessThan(500.0);

        PlaceDetails details = osmPlacesProvider.getPlaceDetails("osm:node:987654");
        assertThat(details.openingHours().rawOpeningHours()).isEqualTo("Mo-Su 07:30-22:00");

        assertThatThrownBy(() -> osmPlacesProvider.getPlaceReviews("osm:node:987654"))
                .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void shouldComputeDirectionsAndDistanceMatrixWithOsrm() {
        wireMockServer.stubFor(get(urlPathMatching("/route/v1/driving/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "Ok",
                                  "routes": [
                                    {
                                      "distance": 14250.5,
                                      "duration": 1850.2,
                                      "geometry": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                                      "legs": [
                                        {
                                          "summary": "VIP Road, AJC Bose Flyover",
                                          "steps": [
                                            {
                                              "distance": 1200.0,
                                              "duration": 180.0,
                                              "name": "VIP Road",
                                              "maneuver": {
                                                "type": "depart",
                                                "modifier": "south",
                                                "location": [88.4467, 22.6547]
                                              }
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """)));

        DirectionsResponse dir = osrmRoutingProvider.getDirections(new DirectionsRequest(
                "22.6547,88.4467", "22.5830,88.3426", TravelMode.DRIVING, List.of(), false, false, false, false
        ));
        assertThat(dir.routeCount()).isEqualTo(1);
        assertThat(dir.primaryRoute().totalDistanceMeters()).isEqualTo(14250.5);
        assertThat(dir.primaryRoute().steps()).hasSize(1);
        assertThat(dir.primaryRoute().steps().getFirst().instruction()).contains("VIP Road");

        wireMockServer.stubFor(get(urlPathMatching("/table/v1/driving/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "Ok",
                                  "durations": [[1850.0]],
                                  "distances": [[14250.0]]
                                }
                                """)));

        DistanceMatrixResponse matrix = osrmRoutingProvider.getDistanceMatrix(new DistanceMatrixRequest(
                List.of("22.6547,88.4467"), List.of("22.5830,88.3426"), TravelMode.DRIVING
        ));
        assertThat(matrix.elements()).hasSize(1);
        assertThat(matrix.elements().getFirst().distanceMeters()).isEqualTo(14250.0);
    }

    @Test
    void shouldIntegrateWithGoogleMapsPlacesDirectionsAndTraffic() {
        wireMockServer.stubFor(get(urlPathEqualTo("/maps/api/place/details/json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "OK",
                                  "result": {
                                    "place_id": "ChIJN1t_tDeuEmsRUsoyG83frY4",
                                    "name": "Indian Museum",
                                    "formatted_address": "27, Jawaharlal Nehru Rd, Kolkata 700016",
                                    "formatted_phone_number": "033 2252 1790",
                                    "rating": 4.6,
                                    "user_ratings_total": 42150,
                                    "business_status": "OPERATIONAL",
                                    "geometry": {
                                      "location": { "lat": 22.5579, "lng": 88.3511 }
                                    },
                                    "opening_hours": {
                                      "open_now": true,
                                      "weekday_text": ["Tuesday: 10:00 AM – 5:00 PM"]
                                    },
                                    "reviews": [
                                      {
                                        "author_name": "Priya Roy",
                                        "rating": 5,
                                        "text": "Oldest and largest museum in India."
                                      }
                                    ]
                                  }
                                }
                                """)));

        PlaceDetails details = googleMapsProvider.getPlaceDetails("ChIJN1t_tDeuEmsRUsoyG83frY4");
        assertThat(details.name()).isEqualTo("Indian Museum");
        assertThat(details.rating()).isEqualTo(4.6);
        assertThat(details.reviews()).hasSize(1);
        assertThat(details.openingHours().openNow()).isTrue();

        wireMockServer.stubFor(get(urlPathEqualTo("/maps/api/directions/json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "OK",
                                  "routes": [
                                    {
                                      "summary": "Maa Flyover",
                                      "overview_polyline": { "points": "_p~iF~ps|U_ulLnnqC" },
                                      "warnings": ["Heavy congestion near Park Circus 7 Point"],
                                      "legs": [
                                        {
                                          "distance": { "value": 12000, "text": "12.0 km" },
                                          "duration": { "value": 1500, "text": "25 mins" },
                                          "duration_in_traffic": { "value": 2100, "text": "35 mins" },
                                          "start_address": "Science City, Kolkata",
                                          "end_address": "Esplanade, Kolkata",
                                          "start_location": { "lat": 22.5390, "lng": 88.3958 },
                                          "end_location": { "lat": 22.5646, "lng": 88.3521 },
                                          "steps": []
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """)));

        TrafficConditionsResponse traffic = googleMapsProvider.getTrafficConditions(
                new Coordinates(22.55, 88.37), 3000, "Science City", "Esplanade"
        );
        assertThat(traffic.liveTrafficSupported()).isTrue();
        assertThat(traffic.congestionLevel()).isEqualTo("HEAVY");
        assertThat(traffic.durationInTrafficSeconds()).isEqualTo(2100L);
    }

    @Test
    void shouldRetrieveElevationAndTimezoneFromOpenMeteo() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/elevation"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "elevation": [9.0] }
                                """)));

        List<ElevationPoint> elevations = elevationTimezoneProvider.getElevation(
                List.of(new Coordinates(22.5726, 88.3639))
        );
        assertThat(elevations).hasSize(1);
        assertThat(elevations.getFirst().elevationMeters()).isEqualTo(9.0);

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "timezone": "Asia/Kolkata" }
                                """)));

        TimezoneInfo tz = elevationTimezoneProvider.getTimezone(new Coordinates(22.5726, 88.3639), 1730000000L);
        assertThat(tz.timeZoneId()).isEqualTo("Asia/Kolkata");
        assertThat(tz.rawOffsetSeconds()).isEqualTo(19800);
    }
}
