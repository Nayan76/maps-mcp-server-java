package com.example.mapsmcp.providers;

import com.example.mapsmcp.dto.request.DirectionsRequest;
import com.example.mapsmcp.dto.response.DirectionsResponse;

public interface DirectionsProvider extends MapProvider {

    DirectionsResponse getDirections(DirectionsRequest request);
}
