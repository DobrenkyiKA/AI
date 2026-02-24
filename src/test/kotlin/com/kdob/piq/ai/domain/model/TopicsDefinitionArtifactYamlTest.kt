//package com.kdob.piq.ai.domain.model
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
//import com.fasterxml.jackson.module.kotlin.registerKotlinModule
//import org.junit.jupiter.api.Test
//
//class TopicsDefinitionArtifactYamlTest {
//
//    @Test
//    fun `should deserialize TopicDefinition from YAML`() {
//        val yamlContent = """
//            pipeline:
//              id: java-core-interview-v1
//              createdBy: admin
//              createdAt: 2026-02-23
//            topics:
//              - key: java-gc
//                title: JVM Garbage Collection
//                description: desc
//            constraints:
//              targetAudience: backend-engineers
//              experienceLevel: mid-to-senior
//              intendedUsage:
//                - interview
//            generation:
//              questionCount: 6
//              avoidRedundancy: true
//              depth: deep
//            exclusions:
//              - android-runtime
//        """.trimIndent()
//
//        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
//        val def = mapper.readValue(yamlContent, TopicsDefinitionArtifact::class.java)
//
//        assert(def.pipeline.id == "java-core-interview-v1")
//        assert(def.topics.size == 1)
//        assert(def.topics[0].key == "java-gc")
//        assert(def.generation.questionCount == 6)
//        assert(def.exclusions == listOf("android-runtime"))
//    }
//}
