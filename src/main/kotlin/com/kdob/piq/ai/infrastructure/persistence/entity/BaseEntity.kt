package com.kdob.piq.ai.infrastructure.persistence.entity

import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate

@MappedSuperclass
abstract class BaseEntity {
    abstract fun getIdValue(): Long?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null ||
            Hibernate.getClass(this) != Hibernate.getClass(other)
        ) return false
        return getIdValue() != null && getIdValue() == (other as BaseEntity).getIdValue()
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String {
        return "${this.javaClass.simpleName}(id=${this.getIdValue()})"
    }
}
