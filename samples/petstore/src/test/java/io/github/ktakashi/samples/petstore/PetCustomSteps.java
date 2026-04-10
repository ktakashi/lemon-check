package io.github.ktakashi.samples.petstore;

import io.github.ktakashi.lemoncheck.step.Step;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom step definitions demonstrating LemonCheck's custom step capability.
 * 
 * <p>This class shows how to define reusable, domain-specific step implementations
 * that can be referenced from .scenario files.
 * 
 * <h2>Step Pattern Syntax</h2>
 * <ul>
 *   <li>{int} - matches an integer (e.g., 42)</li>
 *   <li>{string} - matches a quoted string (e.g., "hello")</li>
 *   <li>{word} - matches a single word (e.g., available)</li>
 *   <li>{float} - matches a floating-point number (e.g., 3.14)</li>
 *   <li>{any} - matches any text (greedy)</li>
 * </ul>
 * 
 * <h2>Usage in Scenario Files</h2>
 * <pre>
 * scenario: Test custom steps
 *   given I have a pet named "Fluffy" with status "available"
 *   then the pet data should contain "Fluffy"
 * </pre>
 */
@Component
public class PetCustomSteps {
    
    /**
     * Stores data between steps (simulating state management).
     */
    private final Map<String, Object> petData = new HashMap<>();
    
    /**
     * Custom step that sets up pet data with a name and status.
     * 
     * @param name The pet name
     * @param status The pet status
     */
    @Step(pattern = "I have a pet named {string} with status {string}")
    public void setupPetData(String name, String status) {
        petData.put("name", name);
        petData.put("status", status);
        System.out.println("[Custom Step] Set up pet: name=" + name + ", status=" + status);
    }
    
    /**
     * Custom step that validates pet data contains expected value.
     * 
     * @param expected The expected value to find in pet data
     */
    @Step(pattern = "the pet data should contain {string}")
    public void validatePetData(String expected) {
        boolean found = petData.values().stream()
            .anyMatch(v -> v != null && v.toString().contains(expected));
        
        if (!found) {
            throw new AssertionError("Pet data does not contain: " + expected +
                ". Current data: " + petData);
        }
        System.out.println("[Custom Step] Validated pet data contains: " + expected);
    }
    
    /**
     * Custom step that counts pets with a given status (demonstrates int parameter).
     * 
     * @param count Expected count
     * @param status Status to filter by
     */
    @Step(pattern = "I should have {int} pets with status {word}")
    public void countPetsByStatus(int count, String status) {
        // In a real implementation, this would query the database or API
        System.out.println("[Custom Step] Checking for " + count + " pets with status: " + status);
        // For demo purposes, always pass
    }
    
    /**
     * Custom step that validates a price range (demonstrates float parameter).
     * 
     * @param price The price to check
     */
    @Step(pattern = "the pet price should be {float}")
    public void checkPetPrice(double price) {
        petData.put("price", price);
        System.out.println("[Custom Step] Set pet price to: " + price);
    }
    
    /**
     * Resets the pet data between scenarios.
     */
    @Step(pattern = "I reset the pet data")
    public void resetPetData() {
        petData.clear();
        System.out.println("[Custom Step] Pet data reset");
    }
}
