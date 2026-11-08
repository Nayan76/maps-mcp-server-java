package com.example.mapsmcp.providers;

import com.example.mapsmcp.models.Coordinates;
import com.example.mapsmcp.models.ElevationPoint;
import com.example.mapsmcp.models.TimezoneInfo;

import java.util.List;

public interface ElevationProvider extends MapProvider {

    List<ElevationPoint> getElevation(List<Coordinates> locations);

    TimezoneInfo getTimezone(Coordinates coordinates, Long timestampSeconds);
}
