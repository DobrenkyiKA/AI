package com.kdob.piq.ai.infrastructure.web.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [StepNumberValidator::class])
annotation class StepNumber(
    val message: String = "Invalid step number",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class StepNumberValidator : ConstraintValidator<StepNumber, Int> {
    override fun isValid(value: Int?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) return false

        // 1. Is not null
        // 2. Is greater than 0
        // 3. Is less than 100

        return value in 1..<100
    }

}
