package com.example.mapsmcp.exceptions;

public class ProviderApiException extends MapsMcpException {

    private final String provider;
    private final int statusCode;

    public ProviderApiException(String provider, int statusCode, String message) {
        super("PROVIDER_API_ERROR", "[" + provider + "] HTTP " + statusCode + ": " + message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public ProviderApiException(String provider, String message, Throwable cause) {
        super("PROVIDER_API_ERROR", "[" + provider + "] " + message, cause);
        this.provider = provider;
        this.statusCode = 502;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
