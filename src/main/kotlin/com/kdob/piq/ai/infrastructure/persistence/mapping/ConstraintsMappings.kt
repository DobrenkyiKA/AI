package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.Constraints
import com.kdob.piq.ai.infrastructure.persistence.entity.ConstraintsEntity

fun Constraints.toEntity(): ConstraintsEntity =
    ConstraintsEntity(
       targetAudience = targetAudience,
        experienceLevel = experienceLevel,
        intendedUsage = intendedUsage,
        exclusions = exclusions,
        questionCount = questionCount
    )

fun ConstraintsEntity.toDomain(): Constraints =
    Constraints(
        targetAudience = targetAudience,
        experienceLevel = experienceLevel,
        intendedUsage = intendedUsage,
        exclusions = exclusions,
        questionCount = questionCount
    )