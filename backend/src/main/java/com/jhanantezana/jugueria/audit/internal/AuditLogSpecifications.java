package com.jhanantezana.jugueria.audit.internal;

import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

// Only non-null filters add a predicate, so an occurred_at range (when given) reaches the query
// as a plain comparison the planner can use to prune partitions, never wrapped in an OR-with-null check.
final class AuditLogSpecifications {

	private AuditLogSpecifications() {
	}

	static Specification<AuditLog> matching(AuditSearchFilter filter) {
		return (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			if (filter.actorId() != null) {
				predicates.add(builder.equal(root.get("actorId"), filter.actorId()));
			}
			if (filter.entityType() != null) {
				predicates.add(builder.equal(root.get("entityType"), filter.entityType()));
			}
			if (filter.entityId() != null) {
				predicates.add(builder.equal(root.get("entityId"), filter.entityId()));
			}
			if (filter.action() != null) {
				predicates.add(builder.equal(root.get("action"), filter.action()));
			}
			if (filter.from() != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
			}
			if (filter.to() != null) {
				predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

}
