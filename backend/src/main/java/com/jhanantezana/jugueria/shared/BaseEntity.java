package com.jhanantezana.jugueria.shared;

import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

	@Id
	private UUID id = Ids.newId();

	// With an assigned ID, Spring Data would otherwise treat every entity as existing and merge it.
	@Transient
	private boolean isNew = true;

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}

	@Override
	public final boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BaseEntity entity) || Hibernate.getClass(this) != Hibernate.getClass(entity)) {
			return false;
		}
		return id.equals(entity.getId());
	}

	@Override
	public final int hashCode() {
		return id.hashCode();
	}

}
