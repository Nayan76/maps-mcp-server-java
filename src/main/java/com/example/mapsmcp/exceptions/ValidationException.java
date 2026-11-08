package com.example.mapsmcp.exceptions;

public class ValidationException extends MapsMcpException {

    public ValidationException(String message) {
        super("INVALID_INPUT", message);
    }
}
