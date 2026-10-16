package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// The sweep repeats every 500 ms, so one of its runs lands after the spy is armed.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.shared.idempotency.initial-delay=100ms", "jugueria.shared.idempotency.cleanup-interval=500ms" })
@Import(TestcontainersConfiguration.class)
class JobSchedulingIT {

	@Autowired
	IdempotencyCleanupJob job;

	@MockitoSpyBean
	IdempotencyCleanup cleanup;

	@Test
	void jobsRunOnTheirOwnSchedulerNotOnTheStompHeartbeatThread() throws Exception {
		var thread = new CompletableFuture<String>();
		doAnswer(invocation -> {
			thread.complete(Thread.currentThread().getName());
			return invocation.callRealMethod();
		}).when(cleanup).deleteExpiredBatch();

		var name = thread.get(10, TimeUnit.SECONDS);

		assertThat(name).startsWith("job-").doesNotStartWith("stomp-heartbeat-");
	}

}
