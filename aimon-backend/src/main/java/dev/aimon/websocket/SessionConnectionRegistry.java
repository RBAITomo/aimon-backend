package dev.aimon.websocket;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking active WebSocket connections by userId.
 * Used to push pet events and stat updates to connected clients.
 */
@ApplicationScoped
public class SessionConnectionRegistry {

    private final Map<Long, WebSocketConnection> connections = new ConcurrentHashMap<>();

    /**
     * Register a user's active WebSocket connection.
     */
    public void register(Long userId, WebSocketConnection connection) {
        connections.put(userId, connection);
    }

    /**
     * Unregister a user's WebSocket connection on disconnect.
     */
    public void unregister(Long userId) {
        connections.remove(userId);
    }

    /**
     * Get active connection for a user.
     */
    public Optional<WebSocketConnection> getConnection(Long userId) {
        return Optional.ofNullable(connections.get(userId));
    }

    /**
     * Get all currently connected user IDs.
     * Used by scheduler to optimize decay checks.
     */
    public Set<Long> getActiveUserIds() {
        return connections.keySet();
    }
}
