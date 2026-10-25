package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

import jakarta.persistence.EntityManager;

// No cleanup: audit_log is append-only. Every search is scoped to a fresh actor id.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AuditSearchIT {

	static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	EntityManager entityManager;

	@Test
	void reportsTheRealTotalForAPagePastTheEnd() {
		var actor = anActorWithEntries(3);

		var page = new AuditSearch(entityManager).search(forActor(actor), PageRequest.of(5, 2, NEWEST_FIRST));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void reportsTheExactTotalOnAFullFirstPage() {
		var actor = anActorWithEntries(3);

		var page = new AuditSearch(entityManager).search(forActor(actor), PageRequest.of(0, 2, NEWEST_FIRST));

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void stopsCountingAtTheCap() {
		var actor = anActorWithEntries(5);
		var search = new AuditSearch(entityManager, 2);

		assertThat(search.search(forActor(actor), PageRequest.of(0, 1, NEWEST_FIRST)).getTotalElements()).isEqualTo(2);
		assertThat(search.search(forActor(actor), PageRequest.of(9, 1, NEWEST_FIRST)).getTotalElements()).isEqualTo(2);
	}

	@Test
	void reportsZeroWhenNothingMatches() {
		var page = new AuditSearch(entityManager).search(forActor(UUID.randomUUID()), PageRequest.of(0, 2, NEWEST_FIRST));

		assertThat(page.getTotalElements()).isZero();
	}

	private UUID anActorWithEntries(int count) {
		var actor = UUID.randomUUID();
		for (var i = 0; i < count; i++) {
			auditLogs.save(new AuditLog(Instant.parse("2026-09-26T12:00:00Z").plusSeconds(i), actor, "ADMIN",
					"USER_ROLE_CHANGED", "USER", UUID.randomUUID(), null, null, null, "corr", "127.0.0.1", "junit"));
		}
		return actor;
	}

	private static AuditSearchFilter forActor(UUID actor) {
		return new AuditSearchFilter(actor, null, null, null, null, null);
	}

}
