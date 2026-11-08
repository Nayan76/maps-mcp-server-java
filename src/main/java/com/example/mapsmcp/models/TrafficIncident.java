package com.example.mapsmcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrafficIncident(
        String id,
        String type,
        String severity,
        String description,
        String roadName,
        Coordinates location,
        String startTime,
        String endTime,
        Boolean roadClosed
) {
}
