package com.example.mapsmcp.exceptions;

public class MapsMcpException extends RuntimeException {

    private final String errorCode;

    public MapsMcpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MapsMcpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
