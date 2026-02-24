package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.domain.model.Pipeline
import com.kdob.piq.ai.domain.model.PipelineStatus
import com.kdob.piq.ai.domain.repository.PipelineRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class PipelineRepositoryImpl : PipelineRepository {
    override fun save(pipeline: Pipeline) = TODO()
    override fun findById(id: UUID): Pipeline? = TODO()
    override fun updateStatus(id: UUID, status: PipelineStatus) = TODO("Because I have PAWS!")
}