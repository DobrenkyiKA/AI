package com.kdob.piq.ai.infrastructure.web.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TopicKeyValidatorTest {

    private val validator = TopicKeyValidator()

    @ParameterizedTest
    @ValueSource(strings = ["abc", "a-b-c-d", "1-b-c-9", "pipeline-name", "very-long-topic-key-with-many-dashes-but-none-are-consecutive-and-it-is-not-too-long-yet-so-it-should-be-fine-if-it-is-below-two-five-five-characters-length-and-it-is-so-it-should-pass-the-validation-test-case-without-any-problems-at-all"])
    fun `should accept valid topic keys`(key: String) {
        assertTrue(validator.isValid(key, null))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",                     // 1. Is not empty (implicitly checked by length)
            "abc d",                // 2. Does not contain spaces
            "A-b-c",                // 4. Contains only down-case a-z
            "-abcd",                // 5. Does not start with "-"
            "abcd-",                // 6. Does not end with "-"
            "abc--d",               // 7. several "-" in a row
            "ab",                 // 8. Not less than 3 characters
        ]
    )
    fun `should reject invalid topic keys`(key: String) {
        assertFalse(validator.isValid(key, null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["null"])
    fun `should reject null`(key: String?) {
        assertFalse(validator.isValid(null, null))
    }
}
