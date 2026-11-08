package com.example.mapsmcp.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TravelMode {
    DRIVING("driving"),
    WALKING("walking"),
    CYCLING("cycling"),
    TRANSIT("transit");

    private final String value;

    TravelMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TravelMode fromString(String text) {
        if (text == null || text.isBlank()) {
            return DRIVING;
        }
        String normalized = text.trim().toLowerCase();
        return switch (normalized) {
            case "driving", "car", "drive", "auto" -> DRIVING;
            case "walking", "walk", "foot", "pedestrian" -> WALKING;
            case "cycling", "bicycling", "bicycle", "bike" -> CYCLING;
            case "transit", "public_transport", "bus", "train", "subway" -> TRANSIT;
            default -> throw new IllegalArgumentException(
                    "Unsupported travel mode: '" + text + "'. Supported modes: driving, walking, cycling, transit");
        };
    }
}
