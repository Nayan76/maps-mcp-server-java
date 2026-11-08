package com.example.mapsmcp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolResponse<T>(
        boolean success,
        String toolName,
        String provider,
        Instant timestamp,
        T data,
        String message,
        String errorCode,
        Map<String, Object> metadata
) {
    public static <T> McpToolResponse<T> ok(String toolName, String provider, T data) {
        return new McpToolResponse<>(true, toolName, provider, Instant.now(), data, null, null, null);
    }

    public static <T> McpToolResponse<T> ok(String toolName, String provider, T data, String message, Map<String, Object> metadata) {
        return new McpToolResponse<>(true, toolName, provider, Instant.now(), data, message, null, metadata);
    }

    public static <T> McpToolResponse<T> error(String toolName, String provider, String errorCode, String message) {
        return new McpToolResponse<>(false, toolName, provider, Instant.now(), null, message, errorCode, null);
    }

    public static <T> McpToolResponse<T> unsupported(String toolName, String provider, String message, Map<String, Object> metadata) {
        return new McpToolResponse<>(false, toolName, provider, Instant.now(), null, message, "PROVIDER_CAPABILITY_UNSUPPORTED", metadata);
    }
}
