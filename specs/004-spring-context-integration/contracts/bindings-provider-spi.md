# Contract: BindingsProvider SPI

**Module**: lemon-check/junit  
**Package**: io.github.ktakashi.lemoncheck.junit.spi

## Purpose

Service Provider Interface allowing pluggable mechanisms for creating `LemonCheckBindings` instances. Enables Spring integration without coupling the junit module to Spring.

## Interface Definition

```kotlin
package io.github.ktakashi.lemoncheck.junit.spi

import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings

/**
 * Service Provider Interface for creating LemonCheckBindings instances.
 * 
 * Implementations are discovered via Java ServiceLoader.
 * When a test class is annotated with framework-specific annotations
 * (e.g., @LemonCheckContextConfiguration), the corresponding provider
 * handles bindings creation with proper dependency injection.
 */
interface BindingsProvider {
    
    /**
     * Determines if this provider supports the given test class.
     * 
     * @param testClass The test class being executed
     * @return true if this provider can handle bindings for this test class
     */
    fun supports(testClass: Class<*>): Boolean
    
    /**
     * Priority of this provider. Higher values indicate higher priority.
     * When multiple providers support a test class, the one with highest
     * priority is used.
     * 
     * @return Priority value (default implementations should return 0)
     */
    fun priority(): Int = 0
    
    /**
     * Initializes the provider for the given test class.
     * Called once before any scenarios are executed.
     * 
     * For Spring integration, this starts the ApplicationContext.
     * 
     * @param testClass The test class being executed
     */
    fun initialize(testClass: Class<*>)
    
    /**
     * Creates a LemonCheckBindings instance for the given test class.
     * 
     * @param testClass The test class being executed
     * @param bindingsClass The bindings class to instantiate (from @LemonCheckConfiguration)
     * @return The bindings instance
     * @throws IllegalStateException if bindings cannot be created
     */
    fun createBindings(
        testClass: Class<*>,
        bindingsClass: Class<out LemonCheckBindings>
    ): LemonCheckBindings
    
    /**
     * Cleans up resources after test execution completes.
     * Called once after all scenarios have executed.
     * 
     * For Spring integration, this releases the ApplicationContext.
     * 
     * @param testClass The test class that was executed
     */
    fun cleanup(testClass: Class<*>)
}
```

## ServiceLoader Registration

Implementations must register via META-INF/services:

```text
# File: META-INF/services/io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
io.github.ktakashi.lemoncheck.spring.SpringBindingsProvider
```

## Default Behavior

When no `BindingsProvider` supports a test class, `LemonCheckTestEngine` falls back to direct instantiation via reflection (existing behavior).

## Lifecycle Sequence

```
1. LemonCheckTestEngine.execute() starts
2. ServiceLoader discovers BindingsProvider implementations
3. For each class descriptor:
   a. Find provider where supports(testClass) = true (highest priority)
   b. If found: provider.initialize(testClass)
   c. bindings = provider.createBindings(testClass, bindingsClass)
   d. Execute scenarios with bindings
   e. provider.cleanup(testClass)
4. If no provider found: use default reflection instantiation
```

## Error Handling

| Scenario | Expected Behavior |
|----------|-------------------|
| Provider.initialize() throws | Report error, skip test class, call cleanup() |
| Provider.createBindings() throws | Report error with cause, fail test class |
| Provider.cleanup() throws | Log warning, continue with remaining tests |
| Multiple providers with same priority | Use first discovered (implementation-defined order) |
