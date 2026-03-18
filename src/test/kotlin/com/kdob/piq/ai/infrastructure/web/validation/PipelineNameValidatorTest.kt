package com.kdob.piq.ai.infrastructure.web.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PipelineNameValidatorTest {

    private val validator = PipelineNameValidator()

    @ParameterizedTest
    @ValueSource(strings = ["abcde", "a-b-c-d", "pipeline-name", "very-long-pipeline-name-with-many-dashes-but-none-are-consecutive-and-it-is-not-too-long-yet-so-it-should-be-fine-if-it-is-below-two-five-five-characters-length-and-it-is-so-it-should-pass-the-validation-test-case-without-any-problems-at-all"])
    fun `should accept valid pipeline names`(name: String) {
        assertTrue(validator.isValid(name, null))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "",                     // 1. Is not empty (implicitly checked by length)
        "abc d",                // 2. Does not contain spaces
        "A-b-c",                // 4. Contains only down-case a-z
        "abc12",                // 4. Contains only down-case a-z
        "-abcd",                // 5. Does not start with "-"
        "abcd-",                // 5. Does not end with "-"
        "abc--d",               // 5. several "-" in a row
        "abcd",                 // 6. Not less than 5 characters
    ])
    fun `should reject invalid pipeline names`(name: String) {
        assertFalse(validator.isValid(name, null))
    }
    
    @ParameterizedTest
    @ValueSource(strings = ["null"])
    fun `should reject null`(name: String?) {
        assertFalse(validator.isValid(null, null))
    }
}
