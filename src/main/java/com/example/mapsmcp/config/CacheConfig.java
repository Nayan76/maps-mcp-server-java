package com.example.mapsmcp.config;

import com.example.mapsmcp.constants.MapConstants;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(MapsProperties properties) {
        if (!properties.getCache().isEnabled()) {
            return new NoOpCacheManager();
        }
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(
                MapConstants.CACHE_PLACES,
                MapConstants.CACHE_GEOCODING,
                MapConstants.CACHE_DIRECTIONS,
                MapConstants.CACHE_DISTANCE_MATRIX,
                MapConstants.CACHE_ELEVATION,
                MapConstants.CACHE_TIMEZONE,
                MapConstants.CACHE_COUNTRY_CITY
        ));
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getCache().getTtlSeconds()))
                .recordStats());
        return manager;
    }
}
