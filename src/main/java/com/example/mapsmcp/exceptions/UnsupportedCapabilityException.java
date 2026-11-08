package com.example.mapsmcp.exceptions;

public class UnsupportedCapabilityException extends MapsMcpException {

    private final String provider;
    private final String capability;

    public UnsupportedCapabilityException(String provider, String capability, String details) {
        super("PROVIDER_CAPABILITY_UNSUPPORTED",
                "Provider '" + provider + "' does not support capability '" + capability + "'. " + details);
        this.provider = provider;
        this.capability = capability;
    }

    public String getProvider() {
        return provider;
    }

    public String getCapability() {
        return capability;
    }
}
