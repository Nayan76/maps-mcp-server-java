package com.example.mapsmcp.providers;

import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.MapTileData;

public interface MapTileProvider extends MapProvider {

    MapTileData getMapTileData(Coordinates center, int zoomLevel);
}
