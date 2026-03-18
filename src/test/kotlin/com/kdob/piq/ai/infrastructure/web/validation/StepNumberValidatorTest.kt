package com.kdob.piq.ai.infrastructure.web.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StepNumberValidatorTest {

    private val validator = StepNumberValidator()

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 50, 99])
    fun `should accept valid step numbers`(stepNumber: Int) {
        assertTrue(validator.isValid(stepNumber, null))
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 100, 101])
    fun `should reject invalid step numbers`(stepNumber: Int) {
        assertFalse(validator.isValid(stepNumber, null))
    }

    @Test
    fun `should reject null`() {
        assertFalse(validator.isValid(null, null))
    }
}