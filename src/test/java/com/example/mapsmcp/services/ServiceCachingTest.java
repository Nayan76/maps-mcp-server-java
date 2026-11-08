package com.example.mapsmcp.services;

import com.example.mapsmcp.config.CacheConfig;
import com.example.mapsmcp.config.MapsProperties;
import com.example.mapsmcp.dto.request.SearchPlacesRequest;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.PlaceSummary;
import com.example.mapsmcp.providers.PlacesProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.validation.GeoValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {PlacesService.class, CacheConfig.class, MapsProperties.class, GeoValidator.class})
class ServiceCachingTest {

    @Autowired
    private PlacesService placesService;

    @MockitoBean
    private ProviderSelector providerSelector;

    @Test
    void shouldCacheRepeatedPlaceSearches() {
        PlacesProvider mockProvider = mock(PlacesProvider.class);
        PlaceSummary summary = new PlaceSummary(
                "id-1", "Coffee House", "College Street, Kolkata", "700073",
                new Coordinates(22.5764, 88.3632), List.of("cafe"),
                null, null, null, null, null, "OPERATIONAL", null, null, "OpenStreetMap", null
        );
        when(providerSelector.selectPlacesProvider()).thenReturn(mockProvider);
        when(mockProvider.searchPlaces(any())).thenReturn(List.of(summary));

        SearchPlacesRequest req = new SearchPlacesRequest(
                "Coffee House College Street", 22.57, 88.36, 5000, "cafe", null, null, 10
        );

        List<PlaceSummary> firstCall = placesService.searchPlaces(req);
        List<PlaceSummary> secondCall = placesService.searchPlaces(req);

        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(1);
        verify(mockProvider, times(1)).searchPlaces(any());
    }
}
