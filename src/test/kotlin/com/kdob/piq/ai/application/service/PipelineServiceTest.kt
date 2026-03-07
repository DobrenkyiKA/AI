package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class PipelineServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val step1QuestionGenerationService = mock(Step1QuestionGenerationService::class.java)
    private val service = PipelineService(repository, artifactStorage, step1QuestionGenerationService)
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    @Test
    fun `should create pipeline with normalized name`() {
        val name = "Java Core Interview v1"
        val expectedNormalized = "java-core-interview-v1"

        `when`(repository.findByName(expectedNormalized)).thenReturn(null)
        `when`(repository.save(any(PipelineEntity::class.java))).thenAnswer { it.arguments[0] as PipelineEntity }

        val result = service.createPipeline(name)

        assertEquals(expectedNormalized, result.name)
        verify(repository).save(any(PipelineEntity::class.java))
    }

    private fun <T> any(type: Class<T>): T {
        org.mockito.Mockito.any(type)
        return if (type == PipelineEntity::class.java) {
            PipelineEntity(name = "dummy") as T
        } else {
            @Suppress("UNCHECKED_CAST")
            null as T
        }
    }

    @Test
    fun `should fail to create pipeline with invalid characters`() {
        val name = "Java Core @ Interview"

        assertThrows<IllegalArgumentException> {
            service.createPipeline(name)
        }
    }

    @Test
    fun `should fail to create pipeline with existing name`() {
        val name = "existing-pipeline"
        `when`(repository.findByName(name)).thenReturn(PipelineEntity(name = name))

        assertThrows<IllegalArgumentException> {
            service.createPipeline(name)
        }
    }

    @Test
    fun `should delete pipeline and its artifacts`() {
        val name = "java-core-interview-v1"

        service.deletePipeline(name)

        verify(repository).deleteByName(name)
        verify(artifactStorage).deleteArtifacts(name)
    }

    @Test
    fun `should load pipeline artifact`() {
        val name = "java-core-interview-v1"
        val expectedYaml = "topics: []"
        `when`(artifactStorage.loadArtifact(name, 0)).thenReturn(expectedYaml)

        val result = service.getPipelineArtifact(name)

        assertEquals(expectedYaml, result)
    }

    @Test
    fun `should update pipeline and its artifacts`() {
        val name = "java-core-interview-v1"
        val yamlContent = """
              topics:
                - key: java-gc-v2
                  title: JVM Garbage Collection v2
                  description: Updated memory management
                  constraints:
                    targetAudience: backend-engineers
                    experienceLevel: mid-to-senior
                    intendedUsage: [interview]
                    exclusions: []
                    questionCount: 8
        """.trimIndent()

        val existingEntity = com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity(name = name)
        `when`(repository.findByName(name)).thenReturn(existingEntity)
        `when`(repository.save(existingEntity)).thenReturn(existingEntity)
        `when`(repository.saveAndFlush(existingEntity)).thenReturn(existingEntity)

        service.updatePipeline(name, yamlContent)

        assert(existingEntity.artifactStep0?.topics?.size == 1)
        assert(existingEntity.artifactStep0?.topics?.first()?.key == "java-gc-v2")
        verify(artifactStorage).saveArtifact(name, 0, yamlContent)
        verify(repository).save(existingEntity)
    }

    @Test
    fun `should run step 1`() {
        val name = "java-core-interview-v1"
        
        service.runStep(name, 1)
        
        verify(step1QuestionGenerationService).generate(name)
    }

    @Test
    fun `should run pipeline from step 0`() {
        val name = "java-core-interview-v1"
        
        service.runPipelineFrom(name, 0)
        
        // step 0 generation currently does nothing, but it's called
        verify(step1QuestionGenerationService).generate(name)
    }

    @Test
    fun `should run pipeline from step 1`() {
        val name = "java-core-interview-v1"
        
        service.runPipelineFrom(name, 1)
        
        verify(step1QuestionGenerationService).generate(name)
    }

    @Test
    fun `should fail for unsupported step run`() {
        val name = "java-core-interview-v1"
        
        assertThrows<IllegalArgumentException> {
            service.runStep(name, 99)
        }
    }
}
