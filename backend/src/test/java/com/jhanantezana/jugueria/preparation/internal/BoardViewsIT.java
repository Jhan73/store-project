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
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class BoardViewsIT {

	private static final UUID JUICE = UUID.randomUUID();

	private static final UUID SMOOTHIE = UUID.randomUUID();

	private static final UUID HONEY = UUID.randomUUID();

	private static final UUID GINGER = UUID.randomUUID();

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
	void ticketsListsWaitingAndPreparingOrdersOldestFirst() {
		var now = Instant.now();
		var newer = accept("L-002", now.minus(1, ChronoUnit.MINUTES), line(JUICE));
		var older = accept("L-001", now.minus(9, ChronoUnit.MINUTES), line(JUICE));
		var preparing = accept("L-003", now.minus(5, ChronoUnit.MINUTES), line(JUICE));
		commands.start(preparing);

		var tickets = queries.tickets(null);

		assertThat(tickets).extracting(BoardOrderView::id).containsExactly(older, preparing, newer);
		assertThat(tickets.getFirst().lines()).hasSize(1);
		assertThat(tickets.getFirst().markers()).hasSize(1);
	}

	@Test
	void ticketsLeavesOutFinishedAndCancelledOrders() {
		var ready = accept("L-001", Instant.now(), line(JUICE));
		commands.start(ready);
		commands.readyOrder(ready);
		var handed = accept("L-002", Instant.now(), line(JUICE));
		commands.start(handed);
		commands.readyOrder(handed);
		commands.handover(handed);
		var cancelled = BoardOrderIntakes.of("L-003", Instant.now(), line(JUICE));
		preparation.accept(cancelled);
		preparation.cancel(cancelled.source(), cancelled.sourceId());
		var waiting = accept("L-004", Instant.now(), line(JUICE));

		assertThat(queries.tickets(null)).extracting(BoardOrderView::id).containsExactly(waiting);
	}

	@Test
	void aScheduledOrderStaysHiddenUntilItsFireTimeAndThenSortsByIt() {
		var now = Instant.now();
		var later = BoardOrderIntakes.scheduled("W-001", now.minus(30, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES),
				line(JUICE));
		preparation.accept(later);
		var due = BoardOrderIntakes.scheduled("W-002", now.minus(30, ChronoUnit.MINUTES), now.minus(2, ChronoUnit.MINUTES),
				line(JUICE));
		var dueId = preparation.accept(due);
		var walkIn = accept("L-001", now.minus(10, ChronoUnit.MINUTES), line(JUICE));

		assertThat(queries.tickets(null)).extracting(BoardOrderView::id).containsExactly(walkIn, dueId);
	}

	@Test
	void theReadyViewListsOnlyReadyOrdersLongestWaitingFirst() {
		var first = accept("L-001", Instant.now(), line(JUICE));
		var second = accept("L-002", Instant.now(), line(JUICE));
		var notReady = accept("L-003", Instant.now(), line(JUICE));
		commands.start(second);
		commands.readyOrder(second);
		commands.start(first);
		commands.readyOrder(first);
		commands.start(notReady);

		assertThat(queries.ready(null)).extracting(BoardOrderView::id).containsExactly(second, first);
	}

	@Test
	void allDayCountsIdenticalItemsAcrossOrdersWhateverTheOrderOfTheirModifiers() {
		accept("L-001", Instant.now(), line(JUICE, "Orange juice", 2, HONEY, GINGER));
		accept("L-002", Instant.now(), line(JUICE, "Orange juice", 1, GINGER, HONEY));
		accept("L-003", Instant.now(), line(JUICE, "Orange juice", 3, HONEY));
		accept("L-004", Instant.now(), line(SMOOTHIE, "Smoothie", 1));

		var allDay = queries.allDay(null);

		assertThat(allDay.groups()).extracting(AllDayView.Group::productId, AllDayView.Group::quantity)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(JUICE, 3), org.assertj.core.groups.Tuple.tuple(JUICE, 3),
					org.assertj.core.groups.Tuple.tuple(SMOOTHIE, 1));
		assertThat(allDay.individual()).isEmpty();
	}

	@Test
	void allDayNeverMergesDifferentModifiersOfTheSameProduct() {
		accept("L-001", Instant.now(), line(JUICE, "Orange juice", 1, HONEY));
		accept("L-002", Instant.now(), line(JUICE, "Orange juice", 1, GINGER));

		assertThat(queries.allDay(null).groups()).hasSize(2).allSatisfy(group -> assertThat(group.quantity()).isEqualTo(1));
	}

	@Test
	void allDayListsLinesWithANoteOrAnAllergySeparately() {
		accept("L-001", Instant.now(), line(JUICE));
		accept("L-002", Instant.now(), line(JUICE));
		var noted = new BoardLineIntake(UUID.randomUUID(), JUICE, "Orange juice", 1, null, List.of(), "no ice", false, null);
		var allergic = new BoardLineIntake(UUID.randomUUID(), JUICE, "Orange juice", 1, null, List.of(), null, true, null);
		accept("L-003", Instant.now(), noted);
		accept("L-004", Instant.now(), allergic);

		var allDay = queries.allDay(null);

		assertThat(allDay.groups()).singleElement().satisfies(group -> assertThat(group.quantity()).isEqualTo(2));
		assertThat(allDay.individual()).extracting(AllDayView.Individual::shortNumber).containsExactly("L-003", "L-004");
		assertThat(allDay.individual().getFirst().note()).isEqualTo("no ice");
		assertThat(allDay.individual().getLast().allergy()).isTrue();
	}

	@Test
	void allDayCountsOnlyLinesStillToPrepare() {
		accept("L-001", Instant.now(), line(JUICE));
		var done = line(JUICE);
		var voided = line(JUICE);
		var intake = BoardOrderIntakes.of("L-002", Instant.now(), done, voided, line(JUICE));
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, done.sourceLineId()));
		preparation.voidLine(intake.source(), intake.sourceId(), voided.sourceLineId(), VoidMode.PENDING_ONLY);
		var hidden = BoardOrderIntakes.scheduled("W-001", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), line(JUICE));
		preparation.accept(hidden);
		var finished = accept("L-003", Instant.now(), line(JUICE));
		commands.start(finished);
		commands.readyOrder(finished);

		assertThat(queries.allDay(null).groups()).singleElement()
			.satisfies(group -> assertThat(group.quantity()).isEqualTo(2));
	}

	@Test
	void theStationFilterKeepsOnlyThatStationsLinesAndOrders() {
		var bar = UUID.randomUUID();
		var kitchen = UUID.randomUUID();
		var mixed = accept("L-001", Instant.now(), at(JUICE, bar), at(SMOOTHIE, kitchen));
		var kitchenOnly = accept("L-002", Instant.now(), at(SMOOTHIE, kitchen));
		accept("L-003", Instant.now(), line(JUICE));

		var tickets = queries.tickets(bar);

		assertThat(tickets).extracting(BoardOrderView::id).containsExactly(mixed);
		assertThat(tickets.getFirst().lines()).singleElement().satisfies(line -> assertThat(line.stationId()).isEqualTo(bar));
		assertThat(queries.tickets(kitchen)).extracting(BoardOrderView::id).containsExactly(mixed, kitchenOnly);
		assertThat(queries.allDay(bar).groups()).singleElement()
			.satisfies(group -> assertThat(group.productId()).isEqualTo(JUICE));
	}

	@Test
	void anOrderStaysReadyOnlyWhenEveryLineOfEveryStationIsReady() {
		var bar = UUID.randomUUID();
		var kitchen = UUID.randomUUID();
		var barLine = at(JUICE, bar);
		var order = accept("L-001", Instant.now(), barLine, at(SMOOTHIE, kitchen));
		commands.start(order);

		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, barLine.sourceLineId()));

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
		assertThat(queries.ready(bar)).isEmpty();
	}

	private UUID accept(String shortNumber, Instant sentAt, BoardLineIntake... lines) {
		return preparation.accept(BoardOrderIntakes.of(shortNumber, sentAt, lines));
	}

	private static BoardLineIntake line(UUID product) {
		return new BoardLineIntake(UUID.randomUUID(), product, "Orange juice", 1, null, List.of(), null, false, null);
	}

	private static BoardLineIntake line(UUID product, String name, int quantity, UUID... options) {
		return new BoardLineIntake(UUID.randomUUID(), product, name, quantity, null, List.of(options), null, false, null);
	}

	private static BoardLineIntake at(UUID product, UUID station) {
		return new BoardLineIntake(UUID.randomUUID(), product, "Item", 1, null, List.of(), null, false, station);
	}

	private static final class BoardOrderIntakes {

		static BoardOrderIntake of(String shortNumber, Instant sentAt, BoardLineIntake... lines) {
			return new BoardOrderIntake(BoardSource.TICKET, UUID.randomUUID(), shortNumber, BoardChannel.TABLE, "Table 4",
					sentAt, null, List.of(lines));
		}

		static BoardOrderIntake scheduled(String shortNumber, Instant sentAt, Instant fireAt, BoardLineIntake... lines) {
			return new BoardOrderIntake(BoardSource.ONLINE, UUID.randomUUID(), shortNumber, BoardChannel.PICKUP, "Ana",
					sentAt, fireAt, List.of(lines));
		}

	}

}
