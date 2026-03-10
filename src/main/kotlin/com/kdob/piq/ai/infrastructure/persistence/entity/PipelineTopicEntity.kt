package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "pipeline_topics")
@Access(AccessType.FIELD)
class PipelineTopicEntity(

    @Basic(optional = false)
    @Column(nullable = false)
    val key: String,

    @Basic(optional = false)
    val name: String,

    @Basic(optional = true)
    @Column(name = "parent_topic_key")
    val parentTopicKey: String?,

    @Basic(optional = false)
    @Column(name = "coverage_area", columnDefinition = "TEXT")
    val coverageArea: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "topics_artifact_id", nullable = false)
    val topicsArtifact: TopicsArtifactEntity,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_topics_sequence")
    @SequenceGenerator(name = "pipeline_topics_sequence", sequenceName = "pipeline_topics_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}