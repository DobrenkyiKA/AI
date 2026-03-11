package com.kdob.piq.ai.infrastructure.persistence.mapping

import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import com.kdob.piq.ai.infrastructure.persistence.entity.TopicsPipelineArtifactEntity
import com.kdob.piq.ai.infrastructure.web.dto.TopicsArtifactForm

fun PipelineEntity.toTopicsArtifactForm(): TopicsArtifactForm {
    val topicsStep = steps.find { it.stepType == "TOPICS_GENERATION" }
    val topicsArtifact = topicsStep?.artifact as? TopicsPipelineArtifactEntity
    val topics = topicsArtifact?.topics?.map { it.toPipelineTopic() }?.toSet() ?: emptySet()
    return TopicsArtifactForm(topics = topics)
}
