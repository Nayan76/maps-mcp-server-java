package com.example.mapsmcp.constants;

public final class MapConstants {

    private MapConstants() {
    }

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;
    public static final double EARTH_RADIUS_KM = 6_371.0088;

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    public static final int DEFAULT_RADIUS_METERS = 5000;
    public static final int MAX_RADIUS_METERS = 50000;
    public static final int DEFAULT_RESULT_LIMIT = 10;
    public static final int MAX_RESULT_LIMIT = 50;

    public static final int MIN_ZOOM_LEVEL = 0;
    public static final int MAX_ZOOM_LEVEL = 19;
    public static final int DEFAULT_ZOOM_LEVEL = 14;

    public static final String CACHE_PLACES = "placesCache";
    public static final String CACHE_GEOCODING = "geocodingCache";
    public static final String CACHE_DIRECTIONS = "directionsCache";
    public static final String CACHE_DISTANCE_MATRIX = "distanceMatrixCache";
    public static final String CACHE_ELEVATION = "elevationCache";
    public static final String CACHE_TIMEZONE = "timezoneCache";
    public static final String CACHE_COUNTRY_CITY = "countryCityCache";

    public static final String USER_AGENT = "GoogleMapsMcpServer/1.0 (Java 21; Spring Boot 3; MCP Compatible)";
}
