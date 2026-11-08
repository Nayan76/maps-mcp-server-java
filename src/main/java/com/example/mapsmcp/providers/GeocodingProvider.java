package com.example.mapsmcp.providers;

import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.CountryCityInfo;
import com.example.mapsmcp.models.GeocodeResult;

import java.util.List;

public interface GeocodingProvider extends MapProvider {

    List<GeocodeResult> geocodeAddress(String address, String countryCode, int limit);

    GeocodeResult reverseGeocode(Coordinates coordinates);

    CountryCityInfo getCountryAndCityInfo(String query, String countryCode);
}
