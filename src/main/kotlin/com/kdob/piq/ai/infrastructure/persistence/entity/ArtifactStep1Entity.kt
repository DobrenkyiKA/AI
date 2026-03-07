package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.ArtifactStatus
import jakarta.persistence.*

@Entity
@Table(name = "artifacts_step_1")
@Access(AccessType.FIELD)
class ArtifactStep1Entity(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    val pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "artifactStep1", cascade = [CascadeType.ALL], orphanRemoval = true)
    val topicsWithQuestions: MutableSet<Step1TopicWithQuestionsEntity> = mutableSetOf()
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "artifacts_step_1_sequence")
    @SequenceGenerator(name = "artifacts_step_1_sequence", sequenceName = "artifacts_step_1_id_sequence", allocationSize = 50)
    var id: Long? = null

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ArtifactStatus = ArtifactStatus.PENDING_FOR_APPROVAL

    override fun getIdValue(): Long? {
        return id
    }
}
