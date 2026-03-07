package com.kdob.piq.ai.application.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kdob.piq.ai.application.service.PipelineService
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import com.kdob.piq.ai.infrastructure.web.dto.PipelineDefinitionForm
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
        .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .registerKotlinModule()

    @Test
    fun `should correctly intake YAML with pipeline root`() {
        val yamlContent = """
            pipeline:
              name: java-core-interview-v1
              topics:
                - key: java-gc
                  title: JVM Garbage Collection
                  description: Memory management
                  constraints:
                    targetAudience: backend-engineers
                    experienceLevel: mid-to-senior
                    intendedUsage: [interview]
                    exclusions: []
                    questionCount: 6
        """.trimIndent()

        val pipeline = yamlMapper.readValue(yamlContent, PipelineDefinitionForm::class.java)
        `when`(repository.save(pipeline)).thenReturn(pipeline.copy(name = "java-core-interview-v1"))
        `when`(repository.findByName("java-core-interview-v1")).thenReturn(
            com.kdob.piq.ai.infrastructure.persistence.entity.PipelineEntity(name = "java-core-interview-v1")
        )

        service.intake(yamlContent)

        // No exception means it worked
    }

    @Test
    fun `should fail to intake YAML without pipeline root`() {
        val yamlContent = """
            name: java-core-interview-v1
            topics:
                - key: java-gc
                  title: JVM Garbage Collection
                  description: Memory management
                  constraints:
                    targetAudience: backend-engineers
                    experienceLevel: mid-to-senior
                    intendedUsage: [interview]
                    exclusions: []
                    questionCount: 6
        """.trimIndent()

        assertThrows<Exception> {
            service.intake(yamlContent)
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
        val expectedYaml = "pipeline: name: $name"
        `when`(artifactStorage.loadArtifact(name, 0)).thenReturn(expectedYaml)

        val result = service.getPipelineArtifact(name)

        assertEquals(expectedYaml, result)
    }

    @Test
    fun `should update pipeline and its artifacts`() {
        val name = "java-core-interview-v1"
        val yamlContent = """
            pipeline:
              name: java-core-interview-v1
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
