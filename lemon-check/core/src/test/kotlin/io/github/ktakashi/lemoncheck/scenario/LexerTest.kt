package io.github.ktakashi.lemoncheck.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {
    @Test
    fun `should tokenize scenario keyword`() {
        val lexer = Lexer("scenario: Test Scenario")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.SCENARIO, tokens[0].type)
        assertEquals(TokenType.COLON, tokens[1].type)
    }

    @Test
    fun `should tokenize step keywords`() {
        val source =
            """
            given the API is available
            when I call the endpoint
            then I get a response
            """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        val types = tokens.map { it.type }
        assertTrue(types.contains(TokenType.GIVEN))
        assertTrue(types.contains(TokenType.WHEN))
        assertTrue(types.contains(TokenType.THEN))
    }

    @Test
    fun `should tokenize string literals`() {
        val lexer = Lexer(""""Hello World"""")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("Hello World", tokens[0].value)
    }

    @Test
    fun `should tokenize escaped strings`() {
        // The input has a literal backslash followed by 'n' inside the string
        val lexer = Lexer("\"Hello\\nWorld\"")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("Hello\nWorld", tokens[0].value)
    }

    @Test
    fun `should tokenize numbers`() {
        val lexer = Lexer("200 3.14 -42")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals("200", tokens[0].value)

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals("3.14", tokens[1].value)

        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertEquals("-42", tokens[2].value)
    }

    @Test
    fun `should tokenize JSON paths`() {
        val lexer = Lexer("$.data.items[0].name")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.JSON_PATH, tokens[0].type)
        assertEquals("$.data.items[0].name", tokens[0].value)
    }

    @Test
    fun `should tokenize variables`() {
        val lexer = Lexer("{{petId}}")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.VARIABLE, tokens[0].type)
        assertEquals("petId", tokens[0].value)
    }

    @Test
    fun `should tokenize operation IDs with caret prefix`() {
        val lexer = Lexer("^listPets ^createPet ^getPetById")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.OPERATION_ID, tokens[0].type)
        assertEquals("listPets", tokens[0].value)

        assertEquals(TokenType.OPERATION_ID, tokens[1].type)
        assertEquals("createPet", tokens[1].value)

        assertEquals(TokenType.OPERATION_ID, tokens[2].type)
        assertEquals("getPetById", tokens[2].value)
    }

    @Test
    fun `should tokenize unprefixed identifiers as IDENTIFIER`() {
        val lexer = Lexer("listPets createPet getPetById")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("listPets", tokens[0].value)

        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("createPet", tokens[1].value)

        assertEquals(TokenType.IDENTIFIER, tokens[2].type)
        assertEquals("getPetById", tokens[2].value)
    }

    @Test
    fun `should produce ERROR for lone caret`() {
        val lexer = Lexer("^")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.ERROR, tokens[0].type)
        assertEquals("Expected identifier after '^'", tokens[0].value)
    }

    @Test
    fun `should produce ERROR for caret followed by number`() {
        val lexer = Lexer("^123")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.ERROR, tokens[0].type)
        assertEquals("Expected identifier after '^'", tokens[0].value)
    }

    @Test
    fun `should produce ERROR for caret followed by space`() {
        val lexer = Lexer("^ listPets")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.ERROR, tokens[0].type)
        assertEquals("Expected identifier after '^'", tokens[0].value)
        // The identifier after the space should be tokenized separately
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("listPets", tokens[1].value)
    }

    @Test
    fun `should handle double caret`() {
        val lexer = Lexer("^^listPets")
        val tokens = lexer.tokenize()

        // First ^ produces an error (followed by another ^, not a letter)
        assertEquals(TokenType.ERROR, tokens[0].type)
        // Second ^listPets produces OPERATION_ID
        assertEquals(TokenType.OPERATION_ID, tokens[1].type)
        assertEquals("listPets", tokens[1].value)
    }

    @Test
    fun `should tokenize operation ID with underscore`() {
        val lexer = Lexer("^List_Pets_123")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.OPERATION_ID, tokens[0].type)
        assertEquals("List_Pets_123", tokens[0].value)
    }

    @Test
    fun `should tokenize arrows`() {
        val lexer = Lexer("=> ->")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.ARROW, tokens[0].type)
        assertEquals("=>", tokens[0].value)

        assertEquals(TokenType.ARROW, tokens[1].type)
        assertEquals("->", tokens[1].value)
    }

    @Test
    fun `should skip comments`() {
        val source =
            """
            # This is a comment
            scenario: Test
            """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        assertEquals(TokenType.SCENARIO, tokens[0].type)
    }

    @Test
    fun `should track indentation`() {
        val source =
            """
            scenario: Test
              given something
                call ^listPets
            """.trimIndent()

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        val types = tokens.map { it.type }
        assertTrue(types.contains(TokenType.INDENT))
    }

    @Test
    fun `should tokenize pipe for examples table`() {
        val lexer = Lexer("| name | age |")
        val tokens = lexer.tokenize()

        assertTrue(tokens.any { it.type == TokenType.PIPE })
    }

    @Test
    fun `should track line and column numbers`() {
        val source =
            """
            line1
            line2
            """.trimIndent()

        val lexer = Lexer(source, "test.scenario")
        val tokens = lexer.tokenize()

        assertEquals(1, tokens[0].location.line)
        assertEquals("test.scenario", tokens[0].location.file)
    }

    @Test
    fun `should tokenize all action keywords`() {
        val source = "call extract assert include using"

        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        assertEquals(TokenType.CALL, tokens[0].type)
        assertEquals(TokenType.EXTRACT, tokens[1].type)
        assertEquals(TokenType.ASSERT, tokens[2].type)
        assertEquals(TokenType.INCLUDE, tokens[3].type)
        assertEquals(TokenType.USING, tokens[4].type)
    }

    @Test
    fun `should tokenize symbols`() {
        val lexer = Lexer(": = ( ) { } [ ] , .")
        val tokens = lexer.tokenize()

        assertEquals(TokenType.COLON, tokens[0].type)
        assertEquals(TokenType.EQUALS, tokens[1].type)
        assertEquals(TokenType.OPEN_PAREN, tokens[2].type)
        assertEquals(TokenType.CLOSE_PAREN, tokens[3].type)
        assertEquals(TokenType.OPEN_BRACE, tokens[4].type)
        assertEquals(TokenType.CLOSE_BRACE, tokens[5].type)
        assertEquals(TokenType.OPEN_BRACKET, tokens[6].type)
        assertEquals(TokenType.CLOSE_BRACKET, tokens[7].type)
        assertEquals(TokenType.COMMA, tokens[8].type)
        assertEquals(TokenType.DOT, tokens[9].type)
    }
}
