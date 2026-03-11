package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "pipeline_steps")
@Access(AccessType.FIELD)
open class PipelineStepEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    open val pipeline: PipelineEntity,

    @Column(name = "step_type", nullable = false)
    open val stepType: String,

    @Column(name = "step_order", nullable = false)
    open var stepOrder: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_prompt_id")
    open var systemPrompt: PromptEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_prompt_id")
    open var userPrompt: PromptEntity? = null,

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "artifact_id")
    open var artifact: PipelineArtifactEntity? = null
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_steps_sequence")
    @SequenceGenerator(name = "pipeline_steps_sequence", sequenceName = "pipeline_steps_id_sequence", allocationSize = 50)
    open var id: Long? = null

    override fun getIdValue(): Long? = id
}
