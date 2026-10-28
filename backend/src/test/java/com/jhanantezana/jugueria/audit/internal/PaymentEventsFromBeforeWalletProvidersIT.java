package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.PaymentRecorded;
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// A publication written by the previous image has no wallet provider fields and is redelivered after the upgrade.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class PaymentEventsFromBeforeWalletProvidersIT {

	static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void anOldPaymentRecordedReadsWithNoProviderAndStillGetsItsAuditRow() {
		var paymentId = UUID.randomUUID();
		var current = new PaymentRecorded(paymentId, UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.WALLET,
				UUID.randomUUID(), "Yape", Money.of("5.00", PEN), null, null, List.of(), true, UUID.randomUUID(),
				Role.CASHIER, NOW);

		var old = withoutFields(current, PaymentRecorded.class, "walletProviderId", "walletProviderName");

		assertThat(old.walletProviderId()).isNull();
		assertThat(old.walletProviderName()).isNull();
		assertThat(old.method()).isEqualTo(PaymentMethod.WALLET);
		transactionTemplate.executeWithoutResult(status -> events.publishEvent(old));
		var entry = auditLogs.findAll().stream().filter(row -> paymentId.equals(row.getEntityId())).findFirst().orElseThrow();
		assertThat(entry.getAfter()).containsEntry("method", "WALLET").containsEntry("walletProvider", null);
	}

	@Test
	void anOldPaymentVoidedReadsWithNoProviderAndStillGetsItsAuditRow() {
		var paymentId = UUID.randomUUID();
		var current = new PaymentVoided(paymentId, UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.WALLET, "Yape",
				Money.of("5.00", PEN), "Changed mind", null, null, UUID.randomUUID(), Role.CASHIER, NOW);

		var old = withoutFields(current, PaymentVoided.class, "walletProviderName");

		assertThat(old.walletProviderName()).isNull();
		assertThat(old.amount().amount()).isEqualByComparingTo("5.00");
		transactionTemplate.executeWithoutResult(status -> events.publishEvent(old));
		var entry = auditLogs.findAll().stream().filter(row -> paymentId.equals(row.getEntityId())).findFirst().orElseThrow();
		assertThat(entry.getAfter()).containsEntry("state", "VOIDED").containsEntry("walletProvider", null);
	}

	private <T> T withoutFields(T event, Class<T> type, String... fields) {
		var tree = (ObjectNode) json.valueToTree(event);
		for (var field : fields) {
			assertThat(tree.has(field)).isTrue();
			tree.remove(field);
		}
		return json.treeToValue(tree, type);
	}

}
