package com.kdob.piq.ai.infrastructure.persistence.entity

import com.kdob.piq.ai.domain.model.PromptType
import jakarta.persistence.*

@Entity
@Table(name = "prompts")
@Access(AccessType.FIELD)
class PromptEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: PromptType,

    @Column(name = "name", nullable = false, unique = true)
    var name: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "prompts_sequence")
    @SequenceGenerator(name = "prompts_sequence", sequenceName = "prompts_id_sequence", allocationSize = 50)
    var id: Long? = null

    override fun getIdValue(): Long? = id
}
