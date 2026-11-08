package com.example.mapsmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MapsMcpApplication {

    public static void main(String[] args) {
        String transport = System.getenv().getOrDefault("MCP_TRANSPORT",
                System.getProperty("mcp.transport", "http"));

        SpringApplication app = new SpringApplication(MapsMcpApplication.class);
        if ("stdio".equalsIgnoreCase(transport.trim())) {
            System.setProperty("spring.ai.mcp.server.stdio", "true");
            System.setProperty("spring.main.web-application-type", "none");
            System.setProperty("spring.main.banner-mode", "off");
        }
        app.run(args);
    }
}
