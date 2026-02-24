package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.Basic
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn

@Embeddable
class ConstraintsEntity(
    @Basic(optional = false)
    @Column(name = "target_audience", nullable = false)
    val targetAudience: String,

    @Basic(optional = false)
    @Column(name = "experience_level", nullable = false)
    val experienceLevel: String,

    @Basic(optional = false)
    @ElementCollection
    @CollectionTable(name = "intended_usages", joinColumns = [JoinColumn(name = "topic_id")])
    @Column(name = "intended_usage", nullable = false)
    val intendedUsage: List<String>,

    @Basic(optional = false)
    @ElementCollection
    @CollectionTable(name = "exclusions", joinColumns = [JoinColumn(name = "topic_id")])
    @Column( name = "exclusion", nullable = false)
    val exclusions: List<String>,

    @Basic(optional = false)
    @Column(name = "question_count", nullable = false)
    val questionCount: Int
)
