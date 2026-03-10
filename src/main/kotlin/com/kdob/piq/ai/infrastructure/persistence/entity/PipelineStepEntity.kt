package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "pipeline_steps")
@Access(AccessType.FIELD)
class PipelineStepEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    val pipeline: PipelineEntity,

    @Column(name = "step_type", nullable = false)
    val stepType: String,

    @Column(name = "step_order", nullable = false)
    var stepOrder: Int,

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    var systemPrompt: String = "",

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    var userPrompt: String = ""
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_steps_sequence")
    @SequenceGenerator(name = "pipeline_steps_sequence", sequenceName = "pipeline_steps_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? = id
}
