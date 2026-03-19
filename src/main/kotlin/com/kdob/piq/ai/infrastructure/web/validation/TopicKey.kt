package com.kdob.piq.ai.infrastructure.web.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TopicKeyValidator::class])
annotation class TopicKey(
    val message: String = "Invalid topic key",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class TopicKeyValidator : ConstraintValidator<TopicKey, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) return false

        // 1. Is not empty
        // 2. Does not contain spaces
        // 3. Not longer than 255 symbols
        // 4. Contains only down-case a-z letters or "-", or 0-9 digits
        // 5. Does not start with "-", does not end with "-", does not contain several "-" in a row
        // 6. Not less than 3 characters

        if (value.length !in 3..255) return false
        if (!value.all { it in 'a'..'z' || it == '-' || it in '0'..'9' }) return false
        if (value.startsWith("-")) return false
        if (value.endsWith("-")) return false
        if (value.contains("--")) return false

        return true
    }
}
