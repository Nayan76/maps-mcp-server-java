package com.example.mapsmcp.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final long MAX_REQUEST_BYTES = 2 * 1024 * 1024; // 2 MB limit

    @Bean
    public Filter requestSizeAndSecurityHeaderFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                if (request instanceof HttpServletRequest httpReq && response instanceof HttpServletResponse httpResp) {
                    long contentLength = httpReq.getContentLengthLong();
                    if (contentLength > MAX_REQUEST_BYTES) {
                        httpResp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                                "Request payload exceeds maximum allowed size of 2MB.");
                        return;
                    }
                    httpResp.setHeader("X-Content-Type-Options", "nosniff");
                    httpResp.setHeader("X-Frame-Options", "DENY");
                    httpResp.setHeader("Cache-Control", "no-store");
                }
                chain.doFilter(request, response);
            }
        };
    }
}
