package com.example.mapsmcp.config;

import com.example.mapsmcp.enums.ProviderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "maps")
public class MapsProperties {

    @NotBlank
    private String provider = "openstreetmap";

    private boolean fallbackEnabled = true;

    private boolean allowLocalhostUrls = false;

    @Min(500)
    @Max(60000)
    private int connectTimeoutMs = 5000;

    @Min(1000)
    @Max(120000)
    private int readTimeoutMs = 15000;

    private final GoogleProperties google = new GoogleProperties();
    private final NominatimProperties nominatim = new NominatimProperties();
    private final OverpassProperties overpass = new OverpassProperties();
    private final OsrmProperties osrm = new OsrmProperties();
    private final MapboxProperties mapbox = new MapboxProperties();
    private final GraphHopperProperties graphhopper = new GraphHopperProperties();
    private final OpenRouteServiceProperties openrouteservice = new OpenRouteServiceProperties();
    private final OpenElevationProperties openElevation = new OpenElevationProperties();
    private final TileProperties tiles = new TileProperties();
    private final CacheSettings cache = new CacheSettings();

    public ProviderType getPrimaryProviderType() {
        return ProviderType.fromString(provider);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public boolean isAllowLocalhostUrls() {
        return allowLocalhostUrls;
    }

    public void setAllowLocalhostUrls(boolean allowLocalhostUrls) {
        this.allowLocalhostUrls = allowLocalhostUrls;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public GoogleProperties getGoogle() {
        return google;
    }

    public NominatimProperties getNominatim() {
        return nominatim;
    }

    public OverpassProperties getOverpass() {
        return overpass;
    }

    public OsrmProperties getOsrm() {
        return osrm;
    }

    public MapboxProperties getMapbox() {
        return mapbox;
    }

    public GraphHopperProperties getGraphhopper() {
        return graphhopper;
    }

    public OpenRouteServiceProperties getOpenrouteservice() {
        return openrouteservice;
    }

    public OpenElevationProperties getOpenElevation() {
        return openElevation;
    }

    public TileProperties getTiles() {
        return tiles;
    }

    public CacheSettings getCache() {
        return cache;
    }

    public static class GoogleProperties {
        private String apiKey = "";
        @NotBlank
        private String baseUrl = "https://maps.googleapis.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class NominatimProperties {
        @NotBlank
        private String baseUrl = "https://nominatim.openstreetmap.org";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class OverpassProperties {
        @NotBlank
        private String baseUrl = "https://overpass-api.de/api/interpreter";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class OsrmProperties {
        @NotBlank
        private String baseUrl = "https://router.project-osrm.org";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class MapboxProperties {
        private String accessToken = "";
        @NotBlank
        private String baseUrl = "https://api.mapbox.com";

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean hasAccessToken() {
            return accessToken != null && !accessToken.isBlank();
        }
    }

    public static class GraphHopperProperties {
        private String apiKey = "";
        @NotBlank
        private String baseUrl = "https://graphhopper.com/api/1";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class OpenRouteServiceProperties {
        private String apiKey = "";
        @NotBlank
        private String baseUrl = "https://api.openrouteservice.org";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class OpenElevationProperties {
        @NotBlank
        private String baseUrl = "https://api.open-meteo.com";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class TileProperties {
        @NotBlank
        private String osmTileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

        public String getOsmTileUrlTemplate() {
            return osmTileUrlTemplate;
        }

        public void setOsmTileUrlTemplate(String osmTileUrlTemplate) {
            this.osmTileUrlTemplate = osmTileUrlTemplate;
        }
    }

    public static class CacheSettings {
        private boolean enabled = true;
        @Min(60)
        @Max(86400)
        private int ttlSeconds = 3600;
        @Min(100)
        @Max(100000)
        private int maxSize = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
