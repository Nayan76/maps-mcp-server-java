package com.example.mapsmcp.providers;

import com.example.mapsmcp.dto.response.TrafficConditionsResponse;
import com.example.mapsmcp.models.Coordinates;

public interface TrafficProvider extends MapProvider {

    TrafficConditionsResponse getTrafficConditions(Coordinates center, Integer radiusMeters, String origin, String destination);
}
