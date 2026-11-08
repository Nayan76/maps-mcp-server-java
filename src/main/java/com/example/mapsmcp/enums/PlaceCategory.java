package com.example.mapsmcp.enums;

import java.util.Map;

public enum PlaceCategory {
    RESTAURANT("restaurant", "amenity", "restaurant"),
    CAFE("cafe", "amenity", "cafe"),
    HOTEL("hotel", "tourism", "hotel"),
    HOSPITAL("hospital", "amenity", "hospital"),
    PHARMACY("pharmacy", "amenity", "pharmacy"),
    ATM("atm", "amenity", "atm"),
    BANK("bank", "amenity", "bank"),
    PETROL_PUMP("fuel", "amenity", "fuel"),
    GAS_STATION("gas_station", "amenity", "fuel"),
    SHOPPING_MALL("shopping_mall", "shop", "mall"),
    SUPERMARKET("supermarket", "shop", "supermarket"),
    SCHOOL("school", "amenity", "school"),
    UNIVERSITY("university", "amenity", "university"),
    PARKING("parking", "amenity", "parking"),
    POLICE("police", "amenity", "police"),
    TRAIN_STATION("train_station", "railway", "station"),
    BUS_STATION("bus_station", "amenity", "bus_station"),
    AIRPORT("airport", "aeroway", "aerodrome"),
    PARK("park", "leisure", "park"),
    MUSEUM("museum", "tourism", "museum");

    private final String googleType;
    private final String osmKey;
    private final String osmValue;

    PlaceCategory(String googleType, String osmKey, String osmValue) {
        this.googleType = googleType;
        this.osmKey = osmKey;
        this.osmValue = osmValue;
    }

    public String getGoogleType() {
        return googleType;
    }

    public String getOsmKey() {
        return osmKey;
    }

    public String getOsmValue() {
        return osmValue;
    }

    private static final Map<String, PlaceCategory> ALIASES = Map.ofEntries(
            Map.entry("restaurants", RESTAURANT),
            Map.entry("restaurant", RESTAURANT),
            Map.entry("food", RESTAURANT),
            Map.entry("dining", RESTAURANT),
            Map.entry("coffee", CAFE),
            Map.entry("coffee_shop", CAFE),
            Map.entry("coffee shop", CAFE),
            Map.entry("cafe", CAFE),
            Map.entry("cafes", CAFE),
            Map.entry("hotel", HOTEL),
            Map.entry("hotels", HOTEL),
            Map.entry("lodging", HOTEL),
            Map.entry("hospital", HOSPITAL),
            Map.entry("hospitals", HOSPITAL),
            Map.entry("clinic", HOSPITAL),
            Map.entry("pharmacy", PHARMACY),
            Map.entry("chemist", PHARMACY),
            Map.entry("atm", ATM),
            Map.entry("atms", ATM),
            Map.entry("bank", BANK),
            Map.entry("banks", BANK),
            Map.entry("petrol_pump", PETROL_PUMP),
            Map.entry("petrol pump", PETROL_PUMP),
            Map.entry("petrol pumps", PETROL_PUMP),
            Map.entry("fuel", PETROL_PUMP),
            Map.entry("gas_station", GAS_STATION),
            Map.entry("gas station", GAS_STATION),
            Map.entry("shopping_mall", SHOPPING_MALL),
            Map.entry("shopping mall", SHOPPING_MALL),
            Map.entry("mall", SHOPPING_MALL),
            Map.entry("supermarket", SUPERMARKET),
            Map.entry("grocery", SUPERMARKET),
            Map.entry("school", SCHOOL),
            Map.entry("university", UNIVERSITY),
            Map.entry("parking", PARKING),
            Map.entry("police", POLICE),
            Map.entry("train_station", TRAIN_STATION),
            Map.entry("railway_station", TRAIN_STATION),
            Map.entry("bus_station", BUS_STATION),
            Map.entry("airport", AIRPORT),
            Map.entry("park", PARK),
            Map.entry("museum", MUSEUM)
    );

    public static PlaceCategory fromInput(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String normalized = category.trim().toLowerCase().replace('-', '_');
        PlaceCategory matched = ALIASES.get(normalized);
        if (matched != null) {
            return matched;
        }
        for (PlaceCategory pc : values()) {
            if (pc.name().equalsIgnoreCase(normalized) || pc.googleType.equalsIgnoreCase(normalized)) {
                return pc;
            }
        }
        return null;
    }
}
