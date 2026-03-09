package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.domain.model.Constraints
import com.kdob.piq.ai.domain.model.Step0Topic
import com.kdob.piq.ai.infrastructure.persistence.entity.ArtifactStep0Entity
import com.kdob.piq.ai.infrastructure.persistence.entity.Step0TopicEntity

fun Step0Topic.toEntity(artifactStep0: ArtifactStep0Entity): Step0TopicEntity =
    Step0TopicEntity(
        key = key,
        name = name,
        parentTopicKey = parentTopicKey,
        coverageArea = coverageArea,
        artifactStep0 = artifactStep0,
        constraints = (constraints ?: Constraints(
            targetAudience = "senior developer",
            experienceLevel = "5+ years",
            intendedUsage = listOf("interview"),
            exclusions = emptyList(),
            questionCount = 3
        )).toEntity()
    )

    fun Step0TopicEntity.toDomain(): Step0Topic =
        Step0Topic(
            key = key,
            name = name,
            parentTopicKey = parentTopicKey,
            coverageArea = coverageArea,
            constraints = constraints.toDomain()
        )