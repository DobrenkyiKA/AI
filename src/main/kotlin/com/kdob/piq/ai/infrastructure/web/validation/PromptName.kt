package com.kdob.piq.ai.infrastructure.web.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PromptNameValidator::class])
annotation class PromptName(
    val message: String = "Invalid prompt name",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class PromptNameValidator : ConstraintValidator<PromptName, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) return false
        
        // 1. Not empty
        // 2. Minimum 3 characters
        // 3. All uppercase A-Z and underscores
        // 4. No leading or trailing underscores
        
        if (value.length < 3) return false
        if (!value.all { it in 'A'..'Z' || it == '_' }) return false
        if (value.startsWith("_")) return false
        if (value.endsWith("_")) return false
        
        return true
    }
}
