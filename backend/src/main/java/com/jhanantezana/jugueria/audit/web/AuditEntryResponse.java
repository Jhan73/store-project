package com.jhanantezana.jugueria.audit.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.audit.internal.AuditLog;

// entityLabel and actorLabel are what the entity and the actor were called when the change happened; null on rows
// written before they were captured.
record AuditEntryResponse(UUID id, Instant occurredAt, @Nullable UUID actorId, String actorRole, String action,
		String entityType, UUID entityId, @Nullable Map<String, Object> before, @Nullable Map<String, Object> after,
		@Nullable String reason, @Nullable String correlationId, @Nullable String entityLabel,
		@Nullable String actorLabel) {

	static AuditEntryResponse from(AuditLog log) {
		return new AuditEntryResponse(log.getId(), log.getOccurredAt(), log.getActorId(), log.getActorRole(),
				log.getAction(), log.getEntityType(), log.getEntityId(), log.getBefore(), log.getAfter(),
				log.getReason(), log.getCorrelationId(), log.getEntityLabel(), log.getActorLabel());
	}

}
