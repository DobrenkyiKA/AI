package com.kdob.piq.ai.infrastructure.web.dto

import com.kdob.piq.ai.domain.model.Step0Topic

data class Step0ArtifactForm(
    val topics: Set<Step0Topic>
)