package com.jhanantezana.jugueria.audit.internal;

import java.util.UUID;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

	private final int countCap;

	@Autowired
	AuditSearch(EntityManager entityManager) {
		this(entityManager, COUNT_CAP);
	}

	AuditSearch(EntityManager entityManager, int countCap) {
		this.entityManager = entityManager;
		this.countCap = countCap;
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
		var lastPage = content.size() < pageable.getPageSize();
		if (lastPage && (!content.isEmpty() || pageable.getOffset() == 0)) {
			return new PageImpl<>(content, pageable, shown);
		}
		var matching = boundedCount(specification);
		return new PageImpl<>(content, pageable, content.isEmpty() ? matching : Math.max(matching, shown));
	}

	// count(*) over a subquery that stops at the cap, so a huge log is never read whole.
	private long boundedCount(Specification<AuditLog> specification) {
		var builder = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();
		var countQuery = builder.createQuery(Long.class);
		var limited = countQuery.subquery(UUID.class);
		var root = limited.from(AuditLog.class);
		var id = (JpaExpression<UUID>) root.<UUID>get("id").alias("id");
		limited.select(id).where(specification.toPredicate(root, countQuery, builder)).fetch(countCap);
		countQuery.from(limited);
		countQuery.select(builder.count());
		return entityManager.createQuery(countQuery).getSingleResult();
	}

}
