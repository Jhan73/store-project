package com.jhanantezana.jugueria.audit.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * A page of the log with a bounded total. {@code count(*)} over a log that only grows reads every matching row, so
 * the total stops at {@value #COUNT_CAP}: past that the client still pages, it just does not learn how far it goes.
 */
@Component
class AuditSearch {

	static final int COUNT_CAP = 10_000;

	private final EntityManager entityManager;

	AuditSearch(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	Page<AuditLog> search(AuditSearchFilter filter, Pageable pageable) {
		var specification = AuditLogSpecifications.matching(filter);
		var builder = entityManager.getCriteriaBuilder();

		var contentQuery = builder.createQuery(AuditLog.class);
		var contentRoot = contentQuery.from(AuditLog.class);
		contentQuery.select(contentRoot)
			.where(specification.toPredicate(contentRoot, contentQuery, builder))
			.orderBy(QueryUtils.toOrders(pageable.getSort(), contentRoot, builder));
		var content = entityManager.createQuery(contentQuery)
			.setFirstResult((int) pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();

		var shown = pageable.getOffset() + content.size();
		if (content.size() < pageable.getPageSize()) {
			return new PageImpl<>(content, pageable, shown);
		}

		var countQuery = builder.createQuery(UUID.class);
		var countRoot = countQuery.from(AuditLog.class);
		countQuery.select(countRoot.get("id")).where(specification.toPredicate(countRoot, countQuery, builder));
		var matching = entityManager.createQuery(countQuery).setMaxResults(COUNT_CAP + 1).getResultList().size();
		return new PageImpl<>(content, pageable, Math.max(Math.min(matching, COUNT_CAP), shown));
	}

}
