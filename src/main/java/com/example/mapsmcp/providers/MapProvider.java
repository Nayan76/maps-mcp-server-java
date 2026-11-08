package com.example.mapsmcp.providers;

import com.example.mapsmcp.enums.ProviderType;

public interface MapProvider {

    ProviderType getProviderType();

    String getProviderName();

    boolean isConfigured();
}
