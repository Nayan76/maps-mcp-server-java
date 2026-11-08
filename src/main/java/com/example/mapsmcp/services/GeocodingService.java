package com.example.mapsmcp.services;

import com.example.mapsmcp.constants.MapConstants;
import com.example.mapsmcp.exceptions.ProviderApiException;
import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.GeocodeResult;
import com.example.mapsmcp.providers.GeocodingProvider;
import com.example.mapsmcp.providers.ProviderSelector;
import com.example.mapsmcp.validation.GeoValidator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeocodingService {

    private final ProviderSelector providerSelector;
    private final GeoValidator geoValidator;

    public GeocodingService(ProviderSelector providerSelector, GeoValidator geoValidator) {
        this.providerSelector = providerSelector;
        this.geoValidator = geoValidator;
    }

    public String getActiveProviderName() {
        return providerSelector.selectGeocodingProvider().getProviderName();
    }

    @Cacheable(value = MapConstants.CACHE_GEOCODING, key = "'fwd:' + #address + ':' + #countryCode + ':' + #limit")
    public List<GeocodeResult> geocodeAddress(String address, String countryCode, Integer limit) {
        geoValidator.validateNotBlank(address, "address");
        int normLimit = geoValidator.validateAndNormalizeLimit(limit != null ? limit : 5);
        GeocodingProvider provider = providerSelector.selectGeocodingProvider();
        return provider.geocodeAddress(address.trim(), countryCode, normLimit);
    }

    @Cacheable(value = MapConstants.CACHE_GEOCODING, key = "'rev:' + #latitude + ':' + #longitude")
    public GeocodeResult reverseGeocode(Double latitude, Double longitude) {
        geoValidator.validateCoordinates(latitude, longitude);
        GeocodingProvider provider = providerSelector.selectGeocodingProvider();
        return provider.reverseGeocode(new Coordinates(latitude, longitude));
    }

    public GeocodeResult getLocationCoordinates(String locationName, String countryCode) {
        List<GeocodeResult> results = geocodeAddress(locationName, countryCode, 1);
        if (results.isEmpty()) {
            throw new ProviderApiException(getActiveProviderName(), 404,
                    "No coordinates found for location: " + locationName);
        }
        return results.getFirst();
    }

    @Cacheable(value = MapConstants.CACHE_COUNTRY_CITY, key = "#query + ':' + #countryCode")
    public CountryCityInfo getCountryAndCityInfo(String query, String countryCode) {
        geoValidator.validateNotBlank(query, "query");
        GeocodingProvider provider = providerSelector.selectGeocodingProvider();
        return provider.getCountryAndCityInfo(query.trim(), countryCode);
    }
}
