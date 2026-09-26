package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

// Proves the documented exception in backend/CLAUDE.md: audit's plain synchronous @EventListener
// runs inside the publisher's transaction, so a failed audit write rolls back the business change.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditRollbackIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@MockitoBean
	AuditLogRepository auditLogs;

	@AfterEach
	void cleanUp() {
		accounts.deleteAll();
	}

	@Test
	void anAuditWriteFailureRollsBackTheStaffAccountCreation() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
		var email = "rollback-" + UUID.randomUUID() + "@jugueria.pe";

		var result = mvc.post()
			.uri("/api/v1/staff")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"role\":\"CASHIER\"}".formatted(email))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findByEmailIgnoreCase(email)).isEmpty();
	}

}
