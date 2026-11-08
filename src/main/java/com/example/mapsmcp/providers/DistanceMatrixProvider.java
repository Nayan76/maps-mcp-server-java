package com.example.mapsmcp.providers;

import com.example.mapsmcp.dto.request.DistanceMatrixRequest;
import com.example.mapsmcp.dto.response.DistanceMatrixResponse;

public interface DistanceMatrixProvider extends MapProvider {

    DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request);
}
