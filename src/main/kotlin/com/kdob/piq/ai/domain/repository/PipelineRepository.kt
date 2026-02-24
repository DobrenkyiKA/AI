package com.kdob.piq.ai.domain.repository

import com.kdob.piq.ai.domain.model.Pipeline
import com.kdob.piq.ai.domain.model.PipelineStatus
import java.util.*


interface PipelineRepository {

    fun save(pipeline: Pipeline)
    fun findById(id: UUID): Pipeline?
    fun updateStatus(id: UUID, status: PipelineStatus)
}