package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.ArtifactStatus
import jakarta.persistence.*

@Entity
@Table(name = "questions_artifacts")
@Access(AccessType.FIELD)
class QuestionsArtifactEntity(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    val pipeline: PipelineEntity,

    @Basic(optional = false)
    @OneToMany(mappedBy = "questionsArtifact", cascade = [CascadeType.ALL], orphanRemoval = true)
    val topicsWithQuestions: MutableSet<PipelineTopicWithQuestionsEntity> = mutableSetOf()
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "questions_artifacts_sequence")
    @SequenceGenerator(name = "questions_artifacts_sequence", sequenceName = "questions_artifacts_id_sequence", allocationSize = 50)
    var id: Long? = null

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ArtifactStatus = ArtifactStatus.PENDING_FOR_APPROVAL

    override fun getIdValue(): Long? {
        return id
    }
}
