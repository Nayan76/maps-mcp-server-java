package com.example.mapsmcp.utils;

public final class SecretMasker {

    private SecretMasker() {
    }

    public static String maskUrlSecrets(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text
                .replaceAll("(?i)(key|api_key|apikey|access_token|token)=([^&\\s]+)", "$1=***REDACTED***");
    }
}
