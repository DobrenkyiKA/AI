package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "artifacts_step_0")
@Access(AccessType.FIELD)
class ArtifactStep0Entity(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    val pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "artifactStep0", cascade = [CascadeType.ALL], orphanRemoval = true)
    val topics: MutableSet<TopicEntity> = mutableSetOf()
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "artifacts_step_0_sequence")
    @SequenceGenerator(name = "artifacts_step_0_sequence", sequenceName = "artifacts_step_0_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? {
        return id
    }
}
