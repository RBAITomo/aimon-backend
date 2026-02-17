package dev.aimon.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Header factory for PowerMem MCP client.
 * Injects API key into X-API-Key header for all requests.
 */
@ApplicationScoped
public class PowerMemClientHeaderFactory implements ClientHeadersFactory {

    @ConfigProperty(name = "memory.mcp.api-key", defaultValue = "dev-powermem-api-key")
    String apiKey;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("X-API-Key", apiKey);
        headers.add("Content-Type", "application/json");
        return headers;
    }
}
