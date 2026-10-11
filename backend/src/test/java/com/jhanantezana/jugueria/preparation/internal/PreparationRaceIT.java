package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.PreparationTables;

// Real threads against real PostgreSQL: the guarantees live in the conditional updates and the card's row lock.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class PreparationRaceIT {

	private static final int ROUNDS = 12;

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@Test
	void startingAndCancellingTheSameWaitingOrderApplyExactlyOne() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var intake = anOrder(aLine());
			var order = preparation.accept(intake);

			var results = inParallel(2, i -> i == 0 ? () -> commands.start(order)
					: () -> preparation.cancel(intake.source(), intake.sourceId()));

			assertThat(successes(results)).as("round %d", round).isEqualTo(1);
			var status = PreparationTables.orderStatus(jdbc, order);
			if (results.get(0).succeeded()) {
				assertThat(status).isEqualTo("PREPARING");
				assertThat(rejectionCodes(results)).containsExactly("preparation.order-already-started");
			}
			else {
				assertThat(status).isEqualTo("CANCELLED");
				assertThat(rejectionCodes(results)).containsExactly("preparation.order-cancelled");
			}
		}
	}

	@Test
	void parallelStartsStartTheOrderOnce() throws Exception {
		var order = preparation.accept(anOrder(aLine()));

		var results = inParallel(16, i -> () -> commands.start(order));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("preparation.order-already-started");
		assertThat(version(order)).isEqualTo(1);
	}

	@Test
	void aServersVoidAndTheLineBeingReadyApplyExactlyOne() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var line = aLine();
			var intake = anOrder(line, aLine());
			var order = preparation.accept(intake);
			commands.start(order);
			var lineId = PreparationTables.lineIdOf(jdbc, order, line.sourceLineId());

			var results = inParallel(2, i -> i == 0 ? () -> commands.readyLine(lineId)
					: () -> preparation.voidLine(intake.source(), intake.sourceId(), line.sourceLineId(),
							VoidMode.PENDING_ONLY));

			assertThat(successes(results)).as("round %d", round).isEqualTo(1);
			if (results.get(0).succeeded()) {
				assertThat(PreparationTables.lineState(jdbc, lineId)).isEqualTo("READY");
				assertThat(rejectionCodes(results)).containsExactly("preparation.line-already-ready");
			}
			else {
				assertThat(PreparationTables.lineState(jdbc, lineId)).isEqualTo("VOIDED");
				assertThat(rejectionCodes(results)).containsExactly("preparation.line-not-pending");
			}
			assertThat(wasteLines(order)).isZero();
		}
	}

	@Test
	void everyLineReadiedInParallelLeavesTheOrderReady() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var lines = new ArrayList<BoardLineIntake>();
			for (var i = 0; i < 8; i++) {
				lines.add(aLine());
			}
			var order = preparation.accept(anOrder(lines.toArray(BoardLineIntake[]::new)));
			commands.start(order);
			var ids = PreparationTables.lineIds(jdbc, order);

			var results = inParallel(ids.size(), i -> () -> commands.readyLine(ids.get(i)));

			assertThat(results).as("round %d", round).allSatisfy(result -> assertThat(result.failure()).isNull());
			assertThat(PreparationTables.orderStatus(jdbc, order)).as("round %d", round).isEqualTo("READY");
			assertThat(pendingLines(order)).isZero();
		}
	}

	@Test
	void readyingTheWholeOrderAndFlaggingALineNeverLeaveAReadyOrderWithAFlaggedLine() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var order = preparation.accept(anOrder(aLine(), aLine()));
			commands.start(order);
			var flagged = PreparationTables.lineIds(jdbc, order).getFirst();

			var results = inParallel(2, i -> i == 0 ? () -> commands.readyOrder(order) : () -> commands.flagLine(flagged));

			assertThat(successes(results)).as("round %d", round).isEqualTo(1);
			if (results.get(0).succeeded()) {
				assertThat(rejectionCodes(results)).containsExactly("preparation.line-not-pending");
			}
			else {
				assertThat(rejectionCodes(results)).containsExactly("preparation.line-flagged-unavailable");
				assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			}
			assertThat(readyOrdersWithPendingLines()).isZero();
		}
	}

	@Test
	void recallAndHandoverOfAReadyOrderNeverLeaveItHandedOverWithLinesToDo() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var order = preparation.accept(anOrder(aLine()));
			commands.start(order);
			commands.readyOrder(order);

			var results = inParallel(2, i -> i == 0 ? () -> commands.recall(order) : () -> commands.handover(order));

			// Handing over first and recalling right after is legal, so the recall always ends up applied.
			assertThat(results.get(0).succeeded()).as("round %d", round).isTrue();
			assertThat(rejectionCodes(results)).isSubsetOf("preparation.order-not-ready");
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			assertThat(pendingLines(order)).isEqualTo(1);
			assertThat(handedOverAt(order)).isNull();
		}
	}

	@Test
	void parallelRecallsRecallTheOrderOnce() throws Exception {
		var order = preparation.accept(anOrder(aLine(), aLine()));
		commands.start(order);
		commands.readyOrder(order);

		var results = inParallel(8, i -> () -> commands.recall(order));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("preparation.order-not-completed");
		assertThat(pendingLines(order)).isEqualTo(2);
	}

	@Test
	void parallelHandoversHandTheOrderOverOnce() throws Exception {
		var order = preparation.accept(anOrder(aLine()));
		commands.start(order);
		commands.readyOrder(order);

		var results = inParallel(8, i -> () -> commands.handover(order));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("preparation.order-not-ready");
	}

	@Test
	void parallelAcceptsOfTheSameSourceCreateOneCard() throws Exception {
		var line = aLine();
		var intake = anOrder(line);

		var results = inParallel(16, i -> () -> preparation.accept(intake));

		assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(PreparationTables.count(jdbc, "board_line")).isEqualTo(1);
		assertThat(PreparationTables.count(jdbc, "board_marker")).isEqualTo(1);
	}

	@Test
	void parallelSendsOfTheSameExtraLineAddItOnce() throws Exception {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyOrder(order);
		var extra = aLine();

		var results = inParallel(8, i -> () -> preparation.addLines(intake.source(), intake.sourceId(), List.of(extra)));

		assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
		assertThat(PreparationTables.lineIds(jdbc, order)).hasSize(2);
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "MODIFIED*");
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
	}

	@Test
	void parallelAcknowledgementsOfOneMarkerApplyOnce() throws Exception {
		var order = preparation.accept(anOrder(aLine()));
		var marker = PreparationTables.markerId(jdbc, order, "NEW");

		var results = inParallel(8, i -> () -> commands.acknowledge(marker, UUID.randomUUID()));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("preparation.marker-already-acknowledged");
	}

	@Test
	void voidingTheLastTwoLinesInParallelCancelsTheOrderWithoutDeadlock() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var first = aLine();
			var second = aLine();
			var intake = anOrder(first, second);
			var order = preparation.accept(intake);

			var results = inParallel(2, i -> () -> preparation.voidLine(intake.source(), intake.sourceId(),
					i == 0 ? first.sourceLineId() : second.sourceLineId(), VoidMode.PENDING_ONLY));

			assertThat(results).as("round %d", round).allSatisfy(result -> assertThat(result.failure()).isNull());
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
		}
	}

	@Test
	void parallelVoidsOfTheOnlyLineBothSucceedWhicheverCancelsTheCard() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var only = aLine();
			var intake = anOrder(only);
			var order = preparation.accept(intake);

			var results = inParallel(2, i -> () -> preparation.voidLine(intake.source(), intake.sourceId(),
					only.sourceLineId(), VoidMode.PENDING_ONLY));

			assertThat(results).as("round %d", round).allSatisfy(result -> assertThat(result.failure()).isNull());
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
			assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "VOID*");
		}
	}

	@Test
	void addingALineAndReadyingTheLastOneNeverLeaveAReadyOrderWithAPendingLine() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var intake = anOrder(aLine());
			var order = preparation.accept(intake);
			commands.start(order);
			var lineId = PreparationTables.lineIds(jdbc, order).getFirst();
			var extra = aLine();

			var results = inParallel(2, i -> i == 0 ? () -> commands.readyLine(lineId)
					: () -> preparation.addLines(intake.source(), intake.sourceId(), List.of(extra)));

			assertThat(results).as("round %d", round).allSatisfy(result -> assertThat(result.failure()).isNull());
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			assertThat(pendingLines(order)).isEqualTo(1);
			assertThat(readyOrdersWithPendingLines()).isZero();
		}
	}

	@Test
	void addingALineAndReadyingTheWholeOrderNeverReadiesTheLineAddedMeanwhile() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var intake = anOrder(aLine());
			var order = preparation.accept(intake);
			commands.start(order);
			var extra = aLine();

			var results = inParallel(2, i -> i == 0 ? () -> commands.readyOrder(order)
					: () -> preparation.addLines(intake.source(), intake.sourceId(), List.of(extra)));

			var extraId = PreparationTables.lineIdOf(jdbc, order, extra.sourceLineId());
			assertThat(PreparationTables.lineState(jdbc, extraId)).as("round %d", round).isEqualTo("PENDING");
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			assertThat(results.get(1).succeeded()).isTrue();
			if (!results.get(0).succeeded()) {
				assertThat(rejectionCodes(results)).containsExactly("preparation.lines-not-acknowledged");
			}
			assertThat(readyOrdersWithPendingLines()).isZero();
		}
	}

	@Test
	void addingALineAndHandingOverApplyExactlyOne() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var intake = anOrder(aLine());
			var order = preparation.accept(intake);
			commands.start(order);
			commands.readyOrder(order);
			var extra = aLine();

			var results = inParallel(2, i -> i == 0 ? () -> commands.handover(order)
					: () -> preparation.addLines(intake.source(), intake.sourceId(), List.of(extra)));

			assertThat(successes(results)).as("round %d", round).isEqualTo(1);
			if (results.get(0).succeeded()) {
				assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("HANDED_OVER");
				assertThat(rejectionCodes(results)).containsExactly("preparation.order-closed");
				assertThat(PreparationTables.lineIds(jdbc, order)).hasSize(1);
			}
			else {
				assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
				assertThat(rejectionCodes(results)).containsExactly("preparation.order-not-ready");
				assertThat(PreparationTables.lineIds(jdbc, order)).hasSize(2);
			}
		}
	}

	@Test
	void addingALineAndRecallingAReadyOrderLeaveItPreparingWithConsistentLines() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var intake = anOrder(aLine());
			var order = preparation.accept(intake);
			commands.start(order);
			commands.readyOrder(order);
			var extra = aLine();

			var results = inParallel(2, i -> i == 0 ? () -> commands.recall(order)
					: () -> preparation.addLines(intake.source(), intake.sourceId(), List.of(extra)));

			assertThat(results.get(1).succeeded()).as("round %d", round).isTrue();
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			if (results.get(0).succeeded()) {
				assertThat(pendingLines(order)).isEqualTo(2);
			}
			else {
				assertThat(rejectionCodes(results)).containsExactly("preparation.order-not-completed");
				assertThat(pendingLines(order)).isEqualTo(1);
			}
		}
	}

	@Test
	void startingAndFlaggingALineOfAWaitingOrderBothApply() throws Exception {
		for (var round = 0; round < ROUNDS; round++) {
			var order = preparation.accept(anOrder(aLine(), aLine()));
			var flagged = PreparationTables.lineIds(jdbc, order).getFirst();

			var results = inParallel(2, i -> i == 0 ? () -> commands.start(order) : () -> commands.flagLine(flagged));

			assertThat(results).as("round %d", round).allSatisfy(result -> assertThat(result.failure()).isNull());
			assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
			assertThat(jdbc.sql("select flagged_unavailable from preparation.board_line where id = ?")
				.param(flagged)
				.query(Boolean.class)
				.single()).isTrue();
		}
	}

	private long version(UUID order) {
		return jdbc.sql("select version from preparation.board_order where id = ?")
			.param(order)
			.query(Long.class)
			.single();
	}

	private Object handedOverAt(UUID order) {
		return jdbc.sql("select handed_over_at from preparation.board_order where id = ?")
			.param(order)
			.query((rs, row) -> rs.getObject(1))
			.list()
			.getFirst();
	}

	private long pendingLines(UUID order) {
		return jdbc.sql("select count(*) from preparation.board_line where board_order_id = ? and state = 'PENDING'")
			.param(order)
			.query(Long.class)
			.single();
	}

	private long wasteLines(UUID order) {
		return jdbc.sql("select count(*) from preparation.board_line where board_order_id = ? and waste")
			.param(order)
			.query(Long.class)
			.single();
	}

	private long readyOrdersWithPendingLines() {
		return jdbc.sql("""
				select count(*) from preparation.board_order o where o.status = 'READY'
					and exists (select 1 from preparation.board_line l where l.board_order_id = o.id and l.state = 'PENDING')
				""").query(Long.class).single();
	}

	private record Result(@org.jspecify.annotations.Nullable Throwable failure) {

		boolean succeeded() {
			return failure == null;
		}

	}

	private interface Work {

		void run() throws Exception;

	}

	private static List<Result> inParallel(int threads, IntFunction<Work> work) throws Exception {
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(threads)) {
			var futures = new ArrayList<Future<Result>>();
			for (var i = 0; i < threads; i++) {
				var task = work.apply(i);
				Callable<Result> call = () -> {
					ready.countDown();
					go.await();
					try {
						task.run();
						return new Result(null);
					}
					catch (Throwable t) {
						return new Result(t);
					}
				};
				futures.add(pool.submit(call));
			}
			ready.await();
			go.countDown();
			var results = new ArrayList<Result>();
			for (var future : futures) {
				results.add(future.get());
			}
			return results;
		}
	}

	private static long successes(List<Result> results) {
		return results.stream().filter(Result::succeeded).count();
	}

	// A lost race must surface as the business rejection, never as a deadlock or constraint violation.
	private static List<String> rejectionCodes(List<Result> results) {
		return results.stream()
			.filter(result -> !result.succeeded())
			.map(result -> result.failure() instanceof BusinessException e ? e.errorCode().code()
					: String.valueOf(result.failure()))
			.toList();
	}

}
