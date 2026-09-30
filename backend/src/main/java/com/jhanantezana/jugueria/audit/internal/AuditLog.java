package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "audit", name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseEntity {

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor_id")
	private @Nullable UUID actorId;

	// "SYSTEM" is a reserved value valid only here, never assignable to a real user account.
	@Column(name = "actor_role", nullable = false)
	private String actorRole;

	@Column(nullable = false)
	private String action;

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private @Nullable Map<String, Object> before;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private @Nullable Map<String, Object> after;

	@Column
	private @Nullable String reason;

	@Column(name = "correlation_id")
	private @Nullable String correlationId;

	@Column(name = "client_ip")
	private @Nullable String clientIp;

	@Column(name = "user_agent")
	private @Nullable String userAgent;

	public AuditLog(Instant occurredAt, @Nullable UUID actorId, String actorRole, String action, String entityType,
			UUID entityId, @Nullable Map<String, Object> before, @Nullable Map<String, Object> after,
			@Nullable String reason, @Nullable String correlationId, @Nullable String clientIp,
			@Nullable String userAgent) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.actorRole = actorRole;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.before = before;
		this.after = after;
		this.reason = reason;
		this.correlationId = correlationId;
		this.clientIp = clientIp;
		this.userAgent = userAgent;
	}

}
