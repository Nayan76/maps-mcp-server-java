package com.example.mapsmcp.mcp.handlers;

import com.example.mapsmcp.dto.response.McpToolResponse;
import com.example.mapsmcp.exceptions.MapsMcpException;
import com.example.mapsmcp.exceptions.UnsupportedCapabilityException;
import com.example.mapsmcp.utils.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class McpExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(McpExecutionHandler.class);

    public <T> McpToolResponse<T> execute(String toolName, String providerName, Supplier<T> action) {
        try {
            T result = action.get();
            return McpToolResponse.ok(toolName, providerName, result);
        } catch (UnsupportedCapabilityException e) {
            log.warn("MCP Tool '{}' unsupported capability on provider '{}': {}",
                    toolName, e.getProvider(), e.getMessage());
            return McpToolResponse.unsupported(
                    toolName,
                    e.getProvider(),
                    e.getMessage(),
                    Map.of("unsupportedCapability", e.getCapability())
            );
        } catch (MapsMcpException e) {
            String safeMsg = SecretMasker.maskUrlSecrets(e.getMessage());
            log.warn("MCP Tool '{}' error [{}]: {}", toolName, e.getErrorCode(), safeMsg);
            return McpToolResponse.error(toolName, providerName, e.getErrorCode(), safeMsg);
        } catch (IllegalArgumentException e) {
            String safeMsg = SecretMasker.maskUrlSecrets(e.getMessage());
            log.warn("MCP Tool '{}' validation error: {}", toolName, safeMsg);
            return McpToolResponse.error(toolName, providerName, "INVALID_INPUT", safeMsg);
        } catch (Exception e) {
            String safeMsg = SecretMasker.maskUrlSecrets(e.getMessage());
            log.error("MCP Tool '{}' unexpected failure: {}", toolName, safeMsg, e);
            return McpToolResponse.error(toolName, providerName, "INTERNAL_TOOL_ERROR",
                    "Tool execution failed: " + safeMsg);
        }
    }
}
