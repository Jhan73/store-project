package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.StockDeficit;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnStockDeficitIT {

	static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	TransactionTemplate transactionTemplate;

	@BeforeEach
	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void recordsASaleThatTookStockBelowZero() {
		var product = UUID.randomUUID();
		var sale = UUID.randomUUID();
		var actor = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new StockDeficit(product, sale, 2, -2, actor, Role.CASHIER, NOW)));

		var entry = auditLogs.findAll()
			.stream()
			.filter(candidate -> product.equals(candidate.getEntityId()))
			.findFirst()
			.orElseThrow();
		assertThat(entry.getAction()).isEqualTo("STOCK_DEFICIT");
		assertThat(entry.getEntityType()).isEqualTo("STOCK_ITEM");
		assertThat(entry.getActorId()).isEqualTo(actor);
		assertThat(entry.getActorRole()).isEqualTo("CASHIER");
		assertThat(entry.getAfter()).containsEntry("onHand", -2)
			.containsEntry("shortBy", 2)
			.containsEntry("referenceId", sale.toString());
	}

}
