package io.github.ktakashi.samples.petstore.provider;

import io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestProvider;
import io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestValue;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Example custom invalid test provider written in Java for demonstrating extensibility.
 *
 * <p>This provider tests emoji characters in string fields, which might cause
 * encoding issues in some systems.
 *
 * <p>This demonstrates that LemonCheck providers can be written in either
 * Kotlin or Java and registered via ServiceLoader.
 */
public class EmojiTestProvider implements InvalidTestProvider {
    
    @NotNull
    @Override
    public String getTestType() {
        return "emoji";
    }
    
    @Override
    public int getPriority() {
        return 100; // Higher than built-in providers
    }

    @Override
    public boolean canHandle(@NotNull Schema<?> schema) {
        return "string".equals(schema.getType());
    }

    @NotNull
    @Override
    public List<InvalidTestValue> generateInvalidValues(
            @NotNull String fieldName,
            @NotNull Schema<?> schema
    ) {
        return List.of(
            new InvalidTestValue(
                "Test 🎉 emoji 🐱 string 🚀",
                "String with emoji characters"
            ),
            new InvalidTestValue(
                "👨‍👩‍👧‍👦", // Family emoji (ZWJ sequence)
                "Zero-width joiner emoji sequence"
            )
        );
    }
}
