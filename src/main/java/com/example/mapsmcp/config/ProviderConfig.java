package com.example.mapsmcp.config;

import com.example.mapsmcp.validation.GeoValidator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MapsProperties.class)
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

    private final MapsProperties properties;
    private final GeoValidator geoValidator;

    public ProviderConfig(MapsProperties properties, GeoValidator geoValidator) {
        this.properties = properties;
        this.geoValidator = geoValidator;
    }

    @PostConstruct
    public void validateProviderUrls() {
        boolean allowLocalhost = properties.isAllowLocalhostUrls();
        geoValidator.validateProviderUrl(properties.getGoogle().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getNominatim().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getOverpass().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getOsrm().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getMapbox().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getGraphhopper().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getOpenrouteservice().getBaseUrl(), allowLocalhost);
        geoValidator.validateProviderUrl(properties.getOpenElevation().getBaseUrl(), allowLocalhost);

        log.info("Initialized Maps MCP Provider configuration. Primary provider: {}, Fallback enabled: {}",
                properties.getPrimaryProviderType(), properties.isFallbackEnabled());
    }
}
