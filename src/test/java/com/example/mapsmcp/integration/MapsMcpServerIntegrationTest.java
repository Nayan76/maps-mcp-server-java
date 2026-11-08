package com.example.mapsmcp.integration;

import com.example.mapsmcp.constants.MapConstants;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MapsMcpServerIntegrationTest {

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterAll20RequiredMcpTools() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        Set<String> registeredToolNames = Arrays.stream(callbacks)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(registeredToolNames).containsExactlyInAnyOrder(
                "search_places",
                "search_nearby",
                "get_place_details",
                "get_directions",
                "get_distance_matrix",
                "geocode_address",
                "reverse_geocode",
                "autocomplete_location",
                "get_place_photos",
                "get_place_reviews",
                "get_place_opening_hours",
                "get_map_data",
                "get_route_alternatives",
                "get_timezone",
                "get_elevation",
                "get_traffic_conditions",
                "find_nearby_categories",
                "calculate_area",
                "get_location_coordinates",
                "get_country_and_city_info"
        );
    }

    @Test
    void shouldExecuteToolDirectlyViaMcpToolCallback() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        ToolCallback calculateAreaTool = Arrays.stream(callbacks)
                .filter(cb -> cb.getToolDefinition().name().equals("calculate_area"))
                .findFirst()
                .orElseThrow();

        String jsonInput = """
                {
                  "vertices": [
                    {"latitude": 22.5440, "longitude": 88.3410},
                    {"latitude": 22.5440, "longitude": 88.3440},
                    {"latitude": 22.5465, "longitude": 88.3440},
                    {"latitude": 22.5465, "longitude": 88.3410}
                  ]
                }
                """;
        String jsonOutput = calculateAreaTool.call(jsonInput);
        assertThat(jsonOutput)
                .contains("\"success\":true")
                .contains("\"toolName\":\"calculate_area\"")
                .contains("\"areaSquareMeters\"")
                .contains("\"Polygon\"");
    }

    @Test
    void shouldConfigureCaffeineCachesCorrectly() {
        assertThat(cacheManager.getCacheNames()).contains(
                MapConstants.CACHE_PLACES,
                MapConstants.CACHE_GEOCODING,
                MapConstants.CACHE_DIRECTIONS,
                MapConstants.CACHE_DISTANCE_MATRIX,
                MapConstants.CACHE_ELEVATION,
                MapConstants.CACHE_TIMEZONE,
                MapConstants.CACHE_COUNTRY_CITY
        );
    }

    @Test
    void shouldExposeActuatorHealthAndSecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
