package com.example.mapsmcp.tools;

import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.request.SearchNearbyRequest;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;
import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.exceptions.ValidationException;
import com.example.mapsmcp.mcp.handlers.McpExecutionHandler;
import com.example.mapsmcp.mcp.tools.DirectionsTools;
import com.example.mapsmcp.mcp.tools.DistanceTools;
import com.example.mapsmcp.mcp.tools.GeocodingTools;
import com.example.mapsmcp.mcp.tools.GeographyTools;
import com.example.mapsmcp.mcp.tools.MapsTools;
import com.example.mapsmcp.mcp.tools.PlacesTools;
import com.example.mapsmcp.mcp.tools.TrafficTools;
import com.example.mapsmcp.models.AddressDetails;
import com.example.mapsmcp.models.AreaCalculationResult;
import com.example.mapsmcp.models.AutocompleteSuggestion;
import com.example.mapsmcp.models.BoundingBox;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.DistanceMatrixElement;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.models.MapTileData;
import com.example.mapsmcp.models.OpeningHoursInfo;
import com.example.mapsmcp.models.PlaceDetails;
import com.example.mapsmcp.models.PlacePhoto;
import com.example.mapsmcp.models.PlaceReview;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.models.RouteOption;
import com.example.mapsmcp.models.RouteStep;
import com.example.mapsmcp.models.TimezoneInfo;
import com.example.mapsmcp.models.TrafficIncident;
import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.services.DirectionsService;
import com.example.mapsmcp.services.DistanceMatrixService;
import com.example.mapsmcp.services.GeocodingService;
import com.example.mapsmcp.services.GeographyService;
import com.example.mapsmcp.services.PlacesService;
import com.example.mapsmcp.services.TrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllTwentyMcpToolsTest {

    @Mock
    private PlacesService placesService;
    @Mock
    private DirectionsService directionsService;
    @Mock
    private DistanceMatrixService distanceMatrixService;
    @Mock
    private GeocodingService geocodingService;
    @Mock
    private GeographyService geographyService;
    @Mock
    private TrafficService trafficService;

    private PlacesTools placesTools;
    private DirectionsTools directionsTools;
    private DistanceTools distanceTools;
    private GeocodingTools geocodingTools;
    private MapsTools mapsTools;
    private TrafficTools trafficTools;
    private GeographyTools geographyTools;

    @BeforeEach
    void setUp() {
        McpExecutionHandler handler = new McpExecutionHandler();
        MapsProperties props = new MapsProperties();
        props.setProvider("google");

        placesTools = new PlacesTools(placesService, handler);
        directionsTools = new DirectionsTools(directionsService, handler);
        distanceTools = new DistanceTools(distanceMatrixService, handler);
        geocodingTools = new GeocodingTools(geocodingService, handler);
        mapsTools = new MapsTools(geographyService, handler);
        trafficTools = new TrafficTools(trafficService, props, handler);
        geographyTools = new GeographyTools(geographyService, geocodingService, handler);
    }

    // 1. search_places
    @Test
    void testTool01_searchPlaces() {
        PlaceSummary sample = new PlaceSummary(
                "place-1", "Peter Cat", "18A Park Street, Kolkata", "700071",
                new Coordinates(22.5529, 88.3522), List.of("restaurant"),
                4.5, 1240, 2, true, 450.0, "OPERATIONAL", "+91 33 2229 8841", null, "Google Maps Platform", null
        );
        when(placesService.getActiveProviderName()).thenReturn("Google Maps Platform");
        when(placesService.searchPlaces(any(SearchPlacesRequest.class))).thenReturn(List.of(sample));

        McpToolResponse<List<PlaceSummary>> resp = placesTools.searchPlaces(
                "restaurants near Park Street", 22.55, 88.35, 2000, "restaurant", true, 4.0, 5
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("search_places");
        assertThat(resp.data()).hasSize(1);
        assertThat(resp.data().getFirst().name()).isEqualTo("Peter Cat");
    }

    // 2. search_nearby
    @Test
    void testTool02_searchNearby() {
        PlaceSummary hospital = new PlaceSummary(
                "osm:node:99", "SSKM Hospital", "244 AJC Bose Road, Kolkata", "700020",
                new Coordinates(22.5394, 88.3425), List.of("hospital"),
                null, null, null, null, 320.0, "OPERATIONAL", null, null, "OpenStreetMap", null
        );
        when(placesService.getActiveProviderName()).thenReturn("OpenStreetMap");
        when(placesService.searchNearby(any(SearchNearbyRequest.class))).thenReturn(List.of(hospital));

        McpToolResponse<List<PlaceSummary>> resp = placesTools.searchNearby(
                22.54, 88.34, 3000, "hospital", null, null, null, 5
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("search_nearby");
        assertThat(resp.data().getFirst().name()).isEqualTo("SSKM Hospital");
    }

    // 3. get_place_details
    @Test
    void testTool03_getPlaceDetails() {
        PlaceDetails details = new PlaceDetails(
                "ChIJ123", "Victoria Memorial", "Victoria Memorial Hall, 1, Queens Way, Maidan, Kolkata", "700071",
                new Coordinates(22.5448, 88.3426), null, List.of("museum", "tourist_attraction"),
                "033 2223 1890", "+91 33 2223 1890", "https://victoriamemorial-cal.org",
                "https://maps.google.com/?cid=123", 4.7, 85000, null, "OPERATIONAL",
                null, List.of(), List.of(), null, "Google Maps Platform"
        );
        when(placesService.getActiveProviderName()).thenReturn("Google Maps Platform");
        when(placesService.getPlaceDetails("ChIJ123")).thenReturn(details);

        McpToolResponse<PlaceDetails> resp = placesTools.getPlaceDetails("ChIJ123");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_place_details");
        assertThat(resp.data().name()).isEqualTo("Victoria Memorial");
    }

    // 4. get_directions
    @Test
    void testTool04_getDirections() {
        RouteStep step = new RouteStep(1, "Head west on VIP Rd", 5000, "5.00 km", 600, "10 min",
                new Coordinates(22.6547, 88.4467), new Coordinates(22.60, 88.40), "depart", "VIP Rd", "driving");
        RouteOption primary = new RouteOption(
                0, "VIP Rd and Strand Rd", 16500, "16.50 km", 2400, "40 min", 2700L, "45 min",
                new Coordinates(22.6547, 88.4467), "Kolkata Airport",
                new Coordinates(22.5830, 88.3426), "Howrah Railway Station",
                null, "encodedPoly", List.of(), List.of(step), List.of(), false, List.of()
        );
        DirectionsResponse dirResp = new DirectionsResponse(
                "Kolkata Airport", "Howrah Railway Station", "driving", 1, primary, List.of(), "OSRM"
        );
        when(directionsService.getActiveProviderName()).thenReturn("OSRM");
        when(directionsService.getDirections(any(DirectionsRequest.class))).thenReturn(dirResp);

        McpToolResponse<DirectionsResponse> resp = directionsTools.getDirections(
                "Kolkata Airport", "Howrah Railway Station", "driving", List.of(), false, false, false, false
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_directions");
        assertThat(resp.data().primaryRoute().totalDistanceMeters()).isEqualTo(16500);
    }

    // 5. get_distance_matrix
    @Test
    void testTool05_getDistanceMatrix() {
        DistanceMatrixElement el = new DistanceMatrixElement(
                0, 0, "Kolkata", "Durgapur", "OK", 170000.0, "170.00 km", 10800L, "3 hr 0 min", null
        );
        DistanceMatrixResponse dmResp = new DistanceMatrixResponse(
                List.of("Kolkata"), List.of("Durgapur"), "driving", List.of(el), "OSRM"
        );
        when(distanceMatrixService.getActiveProviderName()).thenReturn("OSRM");
        when(distanceMatrixService.getDistanceMatrix(any(DistanceMatrixRequest.class))).thenReturn(dmResp);

        McpToolResponse<DistanceMatrixResponse> resp = distanceTools.getDistanceMatrix(
                List.of("Kolkata"), List.of("Durgapur"), "driving"
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_distance_matrix");
        assertThat(resp.data().elements()).hasSize(1);
    }

    // 6. geocode_address
    @Test
    void testTool06_geocodeAddress() {
        GeocodeResult gr = new GeocodeResult(
                "osm:way:123", "Howrah Bridge, Kolkata, West Bengal, 700001, India",
                new Coordinates(22.5851, 88.3468),
                new AddressDetails("Howrah Bridge, Kolkata", null, "Howrah Bridge", null, "Kolkata", null, "West Bengal", "India", "IN", "700001"),
                null, "bridge", "highway", "OpenStreetMap Nominatim"
        );
        when(geocodingService.getActiveProviderName()).thenReturn("OpenStreetMap Nominatim");
        when(geocodingService.geocodeAddress("Howrah Bridge, Kolkata", "IN", 5)).thenReturn(List.of(gr));

        McpToolResponse<List<GeocodeResult>> resp = geocodingTools.geocodeAddress("Howrah Bridge, Kolkata", "IN", 5);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("geocode_address");
        assertThat(resp.data().getFirst().coordinates().latitude()).isEqualTo(22.5851);
    }

    // 7. reverse_geocode
    @Test
    void testTool07_reverseGeocode() {
        GeocodeResult gr = new GeocodeResult(
                "osm:node:555", "Park Street, Kolkata, West Bengal 700016, India",
                new Coordinates(22.5529, 88.3522),
                new AddressDetails("Park Street, Kolkata", null, "Park Street", null, "Kolkata", null, "West Bengal", "India", "IN", "700016"),
                null, "road", "highway", "OpenStreetMap Nominatim"
        );
        when(geocodingService.getActiveProviderName()).thenReturn("OpenStreetMap Nominatim");
        when(geocodingService.reverseGeocode(22.5529, 88.3522)).thenReturn(gr);

        McpToolResponse<GeocodeResult> resp = geocodingTools.reverseGeocode(22.5529, 88.3522);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("reverse_geocode");
        assertThat(resp.data().addressDetails().postalCode()).isEqualTo("700016");
    }

    // 8. autocomplete_location
    @Test
    void testTool08_autocompleteLocation() {
        AutocompleteSuggestion sug = new AutocompleteSuggestion(
                "osm:node:1", "Victoria Memorial, Kolkata, West Bengal, India",
                "Victoria Memorial", "Kolkata, West Bengal, India", List.of("tourism", "museum"),
                new Coordinates(22.5448, 88.3426), "OpenStreetMap Nominatim"
        );
        when(placesService.getActiveProviderName()).thenReturn("OpenStreetMap Nominatim");
        when(placesService.autocompleteLocation("Victoria Mem", 22.54, 88.34, 10000, 5))
                .thenReturn(List.of(sug));

        McpToolResponse<List<AutocompleteSuggestion>> resp = placesTools.autocompleteLocation(
                "Victoria Mem", 22.54, 88.34, 10000, 5
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("autocomplete_location");
        assertThat(resp.data().getFirst().mainText()).isEqualTo("Victoria Memorial");
    }

    // 9. get_place_photos
    @Test
    void testTool09_getPlacePhotos() {
        PlacePhoto photo = new PlacePhoto(
                "ref-1", 800, 600, "https://commons.wikimedia.org/wiki/File:Victoria_Memorial.jpg",
                List.of("Wikimedia Commons"), "OpenStreetMap"
        );
        when(placesService.getActiveProviderName()).thenReturn("OpenStreetMap");
        when(placesService.getPlacePhotos("osm:way:100", 800)).thenReturn(List.of(photo));

        McpToolResponse<List<PlacePhoto>> resp = placesTools.getPlacePhotos("osm:way:100", 800);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_place_photos");
        assertThat(resp.data()).hasSize(1);
    }

    // 10. get_place_reviews
    @Test
    void testTool10_getPlaceReviews() {
        PlaceReview review = new PlaceReview(
                "Arjun Sen", "https://maps.google.com/user1", 5.0, "2 weeks ago",
                "Iconic landmark with beautiful gardens.", 1730000000L, "en"
        );
        when(placesService.getActiveProviderName()).thenReturn("Google Maps Platform");
        when(placesService.getPlaceReviews("ChIJ123")).thenReturn(List.of(review));

        McpToolResponse<List<PlaceReview>> resp = placesTools.getPlaceReviews("ChIJ123");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_place_reviews");
        assertThat(resp.data().getFirst().rating()).isEqualTo(5.0);
    }

    // 11. get_place_opening_hours
    @Test
    void testTool11_getPlaceOpeningHours() {
        OpeningHoursInfo hours = new OpeningHoursInfo(
                "ChIJ123", "Victoria Memorial", true,
                List.of("Tuesday - Sunday: 10:00 AM – 6:00 PM", "Monday: Closed"),
                "Tu-Su 10:00-18:00", true, "Verified opening hours", "Google Maps Platform"
        );
        when(placesService.getActiveProviderName()).thenReturn("Google Maps Platform");
        when(placesService.getPlaceOpeningHours("ChIJ123")).thenReturn(hours);

        McpToolResponse<OpeningHoursInfo> resp = placesTools.getPlaceOpeningHours("ChIJ123");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_place_opening_hours");
        assertThat(resp.data().openNow()).isTrue();
    }

    // 12. get_map_data
    @Test
    void testTool12_getMapData() {
        MapTileData tileData = new MapTileData(
                new Coordinates(22.5448, 88.3426), 14, 12212, 7125,
                "https://tile.openstreetmap.org/14/12212/7125.png",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                new BoundingBox(22.53, 88.33, 22.55, 88.35),
                new BoundingBox(22.51, 88.31, 22.57, 88.37),
                Map.of("type", "Polygon"), "© OpenStreetMap contributors", "Respect usage policy", "OpenStreetMap"
        );
        when(geographyService.getActiveTileProviderName()).thenReturn("OpenStreetMap");
        when(geographyService.getMapData(22.5448, 88.3426, 14)).thenReturn(tileData);

        McpToolResponse<MapTileData> resp = mapsTools.getMapData(22.5448, 88.3426, 14);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_map_data");
        assertThat(resp.data().tileUrl()).contains("/14/12212/7125.png");
    }

    // 13. get_route_alternatives
    @Test
    void testTool13_getRouteAlternatives() {
        RouteOption r1 = new RouteOption(0, "Route A via Maa Flyover", 15000, "15.00 km", 1800, "30 min", null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, List.of());
        RouteOption r2 = new RouteOption(1, "Route B via AJC Bose Rd", 17200, "17.20 km", 2100, "35 min", null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, List.of());
        DirectionsResponse altResp = new DirectionsResponse("Salt Lake", "Howrah", "driving", 2, r1, List.of(r2), "OSRM");

        when(directionsService.getActiveProviderName()).thenReturn("OSRM");
        when(directionsService.getRouteAlternatives("Salt Lake", "Howrah", "driving")).thenReturn(altResp);

        McpToolResponse<DirectionsResponse> resp = directionsTools.getRouteAlternatives("Salt Lake", "Howrah", "driving");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_route_alternatives");
        assertThat(resp.data().routeCount()).isEqualTo(2);
        assertThat(resp.data().alternativeRoutes()).hasSize(1);
    }

    // 14. get_timezone
    @Test
    void testTool14_getTimezone() {
        TimezoneInfo tz = new TimezoneInfo(
                new Coordinates(22.5726, 88.3639), "Asia/Kolkata", "India Standard Time",
                19800, 0, "2026-11-08T18:00:00+05:30", "Open-Meteo"
        );
        when(geographyService.getActiveElevationProviderName()).thenReturn("Open-Meteo");
        when(geographyService.getTimezone(22.5726, 88.3639, null)).thenReturn(tz);

        McpToolResponse<TimezoneInfo> resp = geographyTools.getTimezone(22.5726, 88.3639, null);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_timezone");
        assertThat(resp.data().timeZoneId()).isEqualTo("Asia/Kolkata");
    }

    // 15. get_elevation
    @Test
    void testTool15_getElevation() {
        Coordinates darjeeling = new Coordinates(27.0410, 88.2663);
        ElevationPoint ep = new ElevationPoint(darjeeling, 2042.5, 90.0, "Open-Meteo");
        when(geographyService.getActiveElevationProviderName()).thenReturn("Open-Meteo");
        when(geographyService.getElevation(List.of(darjeeling))).thenReturn(List.of(ep));

        McpToolResponse<List<ElevationPoint>> resp = geographyTools.getElevation(List.of(darjeeling));
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_elevation");
        assertThat(resp.data().getFirst().elevationMeters()).isEqualTo(2042.5);
    }

    // 16. get_traffic_conditions
    @Test
    void testTool16_getTrafficConditions_supportedAndUnsupported() {
        TrafficConditionsResponse traffic = new TrafficConditionsResponse(
                true, "Kolkata Airport -> Howrah", new Coordinates(22.60, 88.40),
                "MODERATE", 2400L, 2850L, 1.19,
                List.of(new TrafficIncident("inc-1", "CONGESTION", "MODERATE", "Slow traffic near Shyambazar", "BT Road", null, null, null, false)),
                "Google Maps Platform", "Live traffic delay computed from Google Directions"
        );
        when(trafficService.getTrafficConditions(22.60, 88.40, 3000, "Kolkata Airport", "Howrah"))
                .thenReturn(traffic);

        McpToolResponse<TrafficConditionsResponse> resp = trafficTools.getTrafficConditions(
                22.60, 88.40, 3000, "Kolkata Airport", "Howrah"
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_traffic_conditions");
        assertThat(resp.data().congestionLevel()).isEqualTo("MODERATE");

        // Verify never fabricating traffic when provider doesn't support it
        when(trafficService.getTrafficConditions(eq(22.0), eq(88.0), any(), any(), any()))
                .thenThrow(new UnsupportedCapabilityException("openstreetmap", "get_traffic_conditions", "Live traffic not supported by OSM"));

        McpToolResponse<TrafficConditionsResponse> unsupportedResp = trafficTools.getTrafficConditions(
                22.0, 88.0, 3000, null, null
        );
        assertThat(unsupportedResp.success()).isFalse();
        assertThat(unsupportedResp.errorCode()).isEqualTo("PROVIDER_CAPABILITY_UNSUPPORTED");
    }

    // 17. find_nearby_categories
    @Test
    void testTool17_findNearbyCategories() {
        PlaceSummary atm = new PlaceSummary("osm:node:1", "SBI ATM", "Park St", "700016", new Coordinates(22.55, 88.35), List.of("atm"), null, null, null, null, 120.0, "OPERATIONAL", null, null, "OpenStreetMap", null);
        PlaceSummary fuel = new PlaceSummary("osm:node:2", "IndianOil Petrol Pump", "AJC Bose Rd", "700020", new Coordinates(22.54, 88.35), List.of("fuel"), null, null, null, null, 450.0, "OPERATIONAL", null, null, "OpenStreetMap", null);

        when(placesService.getActiveProviderName()).thenReturn("OpenStreetMap");
        when(placesService.findNearbyCategories(22.55, 88.35, List.of("atm", "petrol_pump"), 3000, 3))
                .thenReturn(Map.of("atm", List.of(atm), "petrol_pump", List.of(fuel)));

        McpToolResponse<Map<String, List<PlaceSummary>>> resp = placesTools.findNearbyCategories(
                22.55, 88.35, List.of("atm", "petrol_pump"), 3000, 3
        );
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("find_nearby_categories");
        assertThat(resp.data()).containsKeys("atm", "petrol_pump");
    }

    // 18. calculate_area
    @Test
    void testTool18_calculateArea() {
        List<Coordinates> vertices = List.of(
                new Coordinates(22.5440, 88.3410),
                new Coordinates(22.5440, 88.3440),
                new Coordinates(22.5465, 88.3440),
                new Coordinates(22.5465, 88.3410)
        );
        AreaCalculationResult areaRes = new AreaCalculationResult(
                85600.0, 0.0856, 8.56, 21.15, 1170.0, 1.17,
                new Coordinates(22.54525, 88.3425),
                new BoundingBox(22.5440, 88.3410, 22.5465, 88.3440),
                4, true, Map.of("type", "Polygon"), vertices
        );
        when(geographyService.calculateArea(vertices)).thenReturn(areaRes);

        McpToolResponse<AreaCalculationResult> resp = geographyTools.calculateArea(vertices);
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("calculate_area");
        assertThat(resp.data().areaHectares()).isEqualTo(8.56);
    }

    // 19. get_location_coordinates
    @Test
    void testTool19_getLocationCoordinates() {
        GeocodeResult gr = new GeocodeResult(
                "osm:way:777", "Netaji Subhas Chandra Bose International Airport, Kolkata, West Bengal 700052, India",
                new Coordinates(22.6547, 88.4467),
                new AddressDetails("Kolkata Airport", null, null, "Dum Dum", "Kolkata", null, "West Bengal", "India", "IN", "700052"),
                null, "aerodrome", "aeroway", "OpenStreetMap Nominatim"
        );
        when(geocodingService.getActiveProviderName()).thenReturn("OpenStreetMap Nominatim");
        when(geocodingService.getLocationCoordinates("Kolkata Airport", "IN")).thenReturn(gr);

        McpToolResponse<GeocodeResult> resp = geocodingTools.getLocationCoordinates("Kolkata Airport", "IN");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_location_coordinates");
        assertThat(resp.data().coordinates().latitude()).isEqualTo(22.6547);
    }

    // 20. get_country_and_city_info
    @Test
    void testTool20_getCountryAndCityInfo() {
        CountryCityInfo info = new CountryCityInfo(
                "Kolkata", "Kolkata, West Bengal, India", "city",
                "Kolkata", "West Bengal", "India", "IN",
                new Coordinates(22.5726, 88.3639),
                new BoundingBox(22.45, 88.25, 22.70, 88.48),
                "Asia/Kolkata", 4496694L, "Q1348", "en:Kolkata",
                Map.of("type", "Polygon"), Map.of("capital", "state"), "OpenStreetMap Nominatim"
        );
        when(geocodingService.getActiveProviderName()).thenReturn("OpenStreetMap Nominatim");
        when(geocodingService.getCountryAndCityInfo("Kolkata", "IN")).thenReturn(info);

        McpToolResponse<CountryCityInfo> resp = geographyTools.getCountryAndCityInfo("Kolkata", "IN");
        assertThat(resp.success()).isTrue();
        assertThat(resp.toolName()).isEqualTo("get_country_and_city_info");
        assertThat(resp.data().countryCode()).isEqualTo("IN");
        assertThat(resp.data().population()).isEqualTo(4496694L);
    }

    @Test
    void shouldReturnStructuredErrorResponseOnValidationFailure() {
        when(placesService.getActiveProviderName()).thenReturn("OpenStreetMap");
        when(placesService.searchNearby(any())).thenThrow(new ValidationException("Invalid latitude: 120.0"));

        McpToolResponse<List<PlaceSummary>> resp = placesTools.searchNearby(120.0, 88.0, 1000, "atm", null, null, null, 5);
        assertThat(resp.success()).isFalse();
        assertThat(resp.errorCode()).isEqualTo("INVALID_INPUT");
        assertThat(resp.message()).contains("Invalid latitude: 120.0");
    }
}
