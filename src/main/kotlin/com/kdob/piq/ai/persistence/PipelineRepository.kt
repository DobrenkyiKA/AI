package com.kdob.piq.ai.persistence

import com.kdob.piq.ai.domain.Pipeline
import com.kdob.piq.ai.domain.PipelineStatus
import java.util.*


interface PipelineRepository {

    fun save(pipeline: Pipeline)
    fun findById(id: UUID): Pipeline?
    fun updateStatus(id: UUID, status: PipelineStatus)
}