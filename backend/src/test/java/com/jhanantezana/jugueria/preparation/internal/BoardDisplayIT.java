package com.jhanantezana.jugueria.preparation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class BoardDisplayIT {

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@Autowired
	BoardQueries queries;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@Test
	void preparingHoldsWaitingAndPreparingCardsOldestFirst() {
		var now = Instant.now();
		accept("L-002", BoardChannel.COUNTER, "Beto", now.minus(1, ChronoUnit.MINUTES));
		accept("L-001", BoardChannel.COUNTER, "Ana", now.minus(9, ChronoUnit.MINUTES));
		var started = accept("L-003", BoardChannel.COUNTER, "Carla", now.minus(5, ChronoUnit.MINUTES));
		commands.start(started);

		var display = queries.display();

		assertThat(display.preparing()).extracting(DisplayView.Entry::shortNumber).containsExactly("L-001", "L-003", "L-002");
		assertThat(display.ready()).isEmpty();
	}

	@Test
	void readyHoldsReadyCardsThatHaveWaitedLongestFirst() {
		var first = accept("L-001", BoardChannel.COUNTER, "Ana", Instant.now());
		var second = accept("L-002", BoardChannel.COUNTER, "Beto", Instant.now());
		commands.start(second);
		commands.readyOrder(second);
		commands.start(first);
		commands.readyOrder(first);

		var display = queries.display();

		assertThat(display.ready()).extracting(DisplayView.Entry::shortNumber).containsExactly("L-002", "L-001");
		assertThat(display.preparing()).isEmpty();
	}

	@Test
	void aCardMovesFromPreparingToReadyAndOutOnceHandedOver() {
		var order = accept("L-001", BoardChannel.COUNTER, "Ana", Instant.now());
		commands.start(order);
		commands.readyOrder(order);
		assertThat(queries.display().ready()).hasSize(1);

		commands.handover(order);

		var display = queries.display();
		assertThat(display.preparing()).isEmpty();
		assertThat(display.ready()).isEmpty();
	}

	@Test
	void aCancelledCardIsNotShown() {
		var intake = intake("L-001", BoardChannel.COUNTER, "Ana", Instant.now());
		preparation.accept(intake);
		preparation.cancel(intake.source(), intake.sourceId());

		var display = queries.display();

		assertThat(display.preparing()).isEmpty();
		assertThat(display.ready()).isEmpty();
	}

	@Test
	void aScheduledCardStaysHiddenUntilItsFireTime() {
		var now = Instant.now();
		var later = new BoardOrderIntake(BoardSource.ONLINE, UUID.randomUUID(), "W-001", BoardChannel.PICKUP, "Ana",
				now.minus(30, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES), List.of(PreparationTables.aLine()));
		preparation.accept(later);

		assertThat(queries.display().preparing()).isEmpty();
	}

	@Test
	void showsOnlyTheFirstWordOfAPersonsNameAndNoNameForATableOrABlankLabel() {
		var now = Instant.now();
		accept("L-001", BoardChannel.COUNTER, "Ana Perez Soto", now.minus(4, ChronoUnit.MINUTES));
		accept("L-002", BoardChannel.TABLE, "Table 4", now.minus(3, ChronoUnit.MINUTES));
		accept("L-003", BoardChannel.COUNTER, "   ", now.minus(2, ChronoUnit.MINUTES));
		accept("L-004", BoardChannel.COUNTER, null, now.minus(1, ChronoUnit.MINUTES));

		var display = queries.display();

		assertThat(display.preparing()).extracting(DisplayView.Entry::firstName).containsExactly("Ana", null, null, null);
	}

	private UUID accept(String shortNumber, BoardChannel channel, String label, Instant sentAt) {
		return preparation.accept(intake(shortNumber, channel, label, sentAt));
	}

	private static BoardOrderIntake intake(String shortNumber, BoardChannel channel, String label, Instant sentAt) {
		return new BoardOrderIntake(BoardSource.TICKET, UUID.randomUUID(), shortNumber, channel, label, sentAt, null,
				List.of(PreparationTables.aLine()));
	}

}
