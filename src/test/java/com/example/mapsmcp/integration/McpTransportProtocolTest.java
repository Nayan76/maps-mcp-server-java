package com.example.mapsmcp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class McpTransportProtocolTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDiscoverAndExecuteToolsOverStreamableHttpTransport() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(15))
                .build()) {

            McpSchema.InitializeResult initResult = client.initialize();
            assertThat(initResult.serverInfo().name()).isEqualTo("google-maps-mcp-server");

            McpSchema.ListToolsResult toolsResult = client.listTools();
            Set<String> toolNames = toolsResult.tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toSet());

            assertThat(toolNames).hasSize(20).contains(
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

            McpSchema.CallToolResult callResult = client.callTool(new McpSchema.CallToolRequest(
                    "get_map_data",
                    Map.of("latitude", 22.5448, "longitude", 88.3426, "zoomLevel", 15)
            ));

            assertThat(callResult.isError()).isFalse();
            assertThat(callResult.content()).isNotEmpty();
            String textContent = ((McpSchema.TextContent) callResult.content().getFirst()).text();
            assertThat(textContent)
                    .contains("tile.openstreetmap.org")
                    .contains("viewportBoundingBox");
        }
    }

    @Test
    void shouldDiscoverAndExecuteToolsOverStdioSubprocessTransport() throws Exception {
        File jarFile = new File("target/google-maps-mcp-server.jar");
        assertThat(jarFile).exists();

        ServerParameters params = ServerParameters.builder("java")
                .args(
                        "-Dspring.ai.mcp.server.stdio=true",
                        "-Dspring.main.web-application-type=none",
                        "-Dspring.main.banner-mode=off",
                        "-jar",
                        jarFile.getAbsolutePath()
                )
                .build();

        StdioClientTransport stdioTransport = new StdioClientTransport(params, McpJsonMapper.getDefault());
        try (McpSyncClient stdioClient = McpClient.sync(stdioTransport)
                .requestTimeout(Duration.ofSeconds(25))
                .build()) {

            McpSchema.InitializeResult initResult = stdioClient.initialize();
            assertThat(initResult.serverInfo().name()).isEqualTo("google-maps-mcp-server");

            McpSchema.ListToolsResult toolsResult = stdioClient.listTools();
            assertThat(toolsResult.tools()).hasSize(20);

            McpSchema.CallToolResult areaResult = stdioClient.callTool(new McpSchema.CallToolRequest(
                    "get_map_data",
                    Map.of("latitude", 22.5726, "longitude", 88.3639, "zoomLevel", 14)
            ));
            assertThat(areaResult.isError()).isFalse();
            String text = ((McpSchema.TextContent) areaResult.content().getFirst()).text();
            JsonNode root = objectMapper.readTree(text);
            assertThat(root.path("success").asBoolean()).isTrue();
            assertThat(root.path("toolName").asText()).isEqualTo("get_map_data");
        }
    }
}
