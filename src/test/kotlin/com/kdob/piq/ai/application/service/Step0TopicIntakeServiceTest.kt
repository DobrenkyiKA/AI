package com.kdob.piq.ai.application.service

import com.kdob.piq.ai.application.service.step0.Step0TopicIntakeService
import com.kdob.piq.ai.domain.repository.PipelineRepository
import com.kdob.piq.ai.infrastructure.storage.ArtifactStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class Step0TopicIntakeServiceTest {

    private val repository = mock(PipelineRepository::class.java)
    private val artifactStorage = mock(ArtifactStorage::class.java)
    private val service = Step0TopicIntakeService(repository, artifactStorage)

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
}
