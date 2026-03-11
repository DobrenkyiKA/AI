package com.kdob.piq.ai.infrastructure.persistence

import com.kdob.piq.ai.domain.repository.PromptRepository
import com.kdob.piq.ai.infrastructure.persistence.entity.PromptEntity
import com.kdob.piq.ai.domain.model.PromptType
import org.springframework.stereotype.Repository

@Repository
class JpaPromptRepositoryImpl(
    private val springDataPromptRepository: SpringDataPromptRepository
) : PromptRepository {
    override fun findByName(name: String): PromptEntity? = springDataPromptRepository.findByName(name)
    override fun save(prompt: PromptEntity): PromptEntity = springDataPromptRepository.save(prompt)
    override fun deleteByName(name: String) = springDataPromptRepository.deleteByName(name)
    override fun findAllByType(type: PromptType): List<PromptEntity> = springDataPromptRepository.findAllByType(type)
    override fun findAll(): List<PromptEntity> = springDataPromptRepository.findAll()
}
