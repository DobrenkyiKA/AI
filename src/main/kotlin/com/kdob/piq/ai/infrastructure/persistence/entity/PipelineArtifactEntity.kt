package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.ArtifactStatus
import jakarta.persistence.*

@Entity
@Table(name = "pipeline_artifacts")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Access(AccessType.FIELD)
abstract class PipelineArtifactEntity(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    open val pipeline: PipelineEntity
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_artifacts_sequence")
    @SequenceGenerator(name = "pipeline_artifacts_sequence", sequenceName = "pipeline_artifacts_id_sequence", allocationSize = 50)
    open var id: Long? = null

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: ArtifactStatus = ArtifactStatus.NEW

    override fun getIdValue(): Long? {
        return id
    }
}
