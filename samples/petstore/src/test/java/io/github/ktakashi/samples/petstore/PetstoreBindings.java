package io.github.ktakashi.samples.petstore;

import io.github.ktakashi.lemoncheck.config.Configuration;
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bindings for petstore scenario tests.
 * <p> <p />
 * This class provides runtime configuration for scenario execution,
 * including the dynamically allocated port from Spring Boot's test server.
 * <p> <p />
 * When used with @LemonCheckContextConfiguration, this class is retrieved
 * from Spring's ApplicationContext, enabling @LocalServerPort injection.
 * <p> <p />
 * Note: @Lazy is required because @LocalServerPort is only available
 * after the web server has started, which happens after initial bean creation.
 * <p> <p />
 * Multi-spec support: This bindings class registers multiple OpenAPI specs
 * with different base URLs, demonstrating multi-host API testing:
 * - default (petstore.yaml): Pet API at /api/v1
 * - auth (auth.yaml): Auth API at /auth/api/v1
 * <p> <p />
 * In a real microservices environment, these would be separate hosts:
 * - default: http://petstore-service:8080
 * - auth: http://auth-service:8081
 */
@Component
@Lazy
public class PetstoreBindings implements LemonCheckBindings {

    @LocalServerPort
    private int port;

    @Override
    public Map<String, Object> getBindings() {
        // Don't set global baseUrl here - use getSpecBaseUrls() for per-spec URLs
        // Setting baseUrl here would override all per-spec URLs
        return Map.of();
    }

    @Override
    public String getOpenApiSpec() {
        return "petstore.yaml";
    }

    @Override
    public Map<String, String> getAdditionalSpecs() {
        return Map.of("auth", "auth.yaml");
    }

    /**
     * Provide per-spec base URLs for multi-host API testing.
     * 
     * The petstore API is at /api/v1 while the auth API is at /auth/api/v1.
     * This demonstrates different base URLs for different specs within
     * the same test suite - simulating a microservices architecture.
     */
    @Override
    public Map<String, String> getSpecBaseUrls() {
        String host = "http://localhost:" + port;
        return Map.of(
            "default", host + "/api/v1",      // Pet API
            "auth", host + "/auth/api/v1"     // Auth API (different path prefix)
        );
    }

    @Override
    public void configure(Configuration config) {
        // Don't set a global baseUrl - use per-spec base URLs instead
        // This enables true multi-host API testing
        config.setLogRequests(true);
        config.setLogResponses(true);
    }
}
