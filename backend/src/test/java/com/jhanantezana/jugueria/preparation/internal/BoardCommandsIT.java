package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.BoardChanged;
import com.jhanantezana.jugueria.preparation.BoardLineState;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.LineFlaggedUnavailable;
import com.jhanantezana.jugueria.preparation.LineReady;
import com.jhanantezana.jugueria.preparation.OrderHandedOver;
import com.jhanantezana.jugueria.preparation.OrderReady;
import com.jhanantezana.jugueria.preparation.OrderRecalled;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.preparation.PreparationStarted;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class BoardCommandsIT {

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@Autowired
	BoardQueries queries;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@Test
	void startingAnOrderMovesItToPreparingAndStampsTheMoment() {
		var order = accepted(aLine());

		var card = commands.start(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.PREPARING);
		assertThat(card.startedAt()).isNotNull();
		assertThat(events.stream(PreparationStarted.class)).singleElement()
			.satisfies(event -> assertThat(event.boardOrderId()).isEqualTo(order));
		assertThat(changes()).contains("ORDER_STARTED");
	}

	@Test
	void startingTwiceRejectsTheSecondStart() {
		var order = accepted(aLine());
		commands.start(order);

		assertRejected(() -> commands.start(order), PreparationError.ORDER_ALREADY_STARTED);
	}

	@Test
	void startingACancelledOrderSaysItWasCancelled() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		preparation.cancel(intake.source(), intake.sourceId());

		assertRejected(() -> commands.start(order), PreparationError.ORDER_CANCELLED);
	}

	@Test
	void startingAnUnknownOrderIsNotFound() {
		assertRejected(() -> commands.start(UUID.randomUUID()), PreparationError.ORDER_NOT_FOUND);
	}

	@Test
	void aLineCannotBeReadyBeforeTheOrderIsStarted() {
		var order = accepted(aLine());

		assertRejected(() -> commands.readyLine(lines(order).getFirst()), PreparationError.ORDER_NOT_PREPARING);
	}

	@Test
	void readyingOneOfTwoLinesKeepsTheOrderPreparing() {
		var order = accepted(aLine(), aLine());
		commands.start(order);

		var card = commands.readyLine(lines(order).getFirst());

		assertThat(card.status()).isEqualTo(BoardOrderStatus.PREPARING);
		assertThat(card.lines()).extracting(line -> line.state()).containsExactly(BoardLineState.READY,
				BoardLineState.PENDING);
		assertThat(card.lines().getFirst().readyAt()).isNotNull();
		assertThat(events.stream(LineReady.class)).hasSize(1);
		assertThat(events.stream(OrderReady.class)).isEmpty();
	}

	@Test
	void readyingTheLastLineSetsTheOrderReadyAndPublishesIt() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		var lines = lines(order);
		commands.readyLine(lines.get(0));

		var card = commands.readyLine(lines.get(1));

		assertThat(card.status()).isEqualTo(BoardOrderStatus.READY);
		assertThat(card.readyAt()).isNotNull();
		assertThat(events.stream(OrderReady.class)).singleElement()
			.satisfies(event -> assertThat(event.boardOrderId()).isEqualTo(order));
	}

	@Test
	void readyingALineTwiceRejectsTheSecondTime() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		var line = lines(order).getFirst();
		commands.readyLine(line);

		assertRejected(() -> commands.readyLine(line), PreparationError.LINE_NOT_PENDING);
	}

	@Test
	void aFlaggedLineCannotBeReady() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		var line = lines(order).getFirst();
		commands.flagLine(line);

		assertRejected(() -> commands.readyLine(line), PreparationError.LINE_FLAGGED_UNAVAILABLE);
	}

	@Test
	void readyingAnUnknownLineIsNotFound() {
		assertRejected(() -> commands.readyLine(UUID.randomUUID()), PreparationError.LINE_NOT_FOUND);
	}

	@Test
	void readyingTheWholeOrderMarksEveryPendingLineReady() {
		var order = accepted(aLine(), aLine(), aLine());
		commands.start(order);
		commands.readyLine(lines(order).getFirst());

		var card = commands.readyOrder(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.READY);
		assertThat(card.lines()).allSatisfy(line -> assertThat(line.state()).isEqualTo(BoardLineState.READY));
		assertThat(events.stream(LineReady.class)).hasSize(3);
		assertThat(events.stream(OrderReady.class)).hasSize(1);
	}

	@Test
	void theWholeOrderCannotBeReadyWhileALineIsFlagged() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		commands.flagLine(lines(order).getFirst());

		assertRejected(() -> commands.readyOrder(order), PreparationError.LINE_FLAGGED_UNAVAILABLE);
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
		assertThat(PreparationTables.lineState(jdbc, lines(order).getLast())).isEqualTo("PENDING");
	}

	@Test
	void aLineAddedAfterTheScreenLoadedCannotBeReadyUntilItsLabelIsAcknowledged() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var extra = aLine();
		preparation.addLines(intake.source(), intake.sourceId(), java.util.List.of(extra));
		var extraLine = PreparationTables.lineIdOf(jdbc, order, extra.sourceLineId());

		assertRejected(() -> commands.readyLine(extraLine), PreparationError.LINES_NOT_ACKNOWLEDGED);

		assertThat(PreparationTables.lineState(jdbc, extraLine)).isEqualTo("PENDING");
		assertThat(events.stream(LineReady.class)).isEmpty();
	}

	@Test
	void theWholeOrderCannotBeReadyWhileALineAddedAfterItWasShownIsUnacknowledged() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var extra = aLine();
		preparation.addLines(intake.source(), intake.sourceId(), java.util.List.of(extra));

		assertRejected(() -> commands.readyOrder(order), PreparationError.LINES_NOT_ACKNOWLEDGED);

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
		assertThat(PreparationTables.lineState(jdbc, PreparationTables.lineIdOf(jdbc, order, extra.sourceLineId())))
			.isEqualTo("PENDING");
		assertThat(events.stream(LineReady.class)).isEmpty();
	}

	@Test
	void theWholeOrderCanBeReadyOnceTheAddedLinesAreAcknowledged() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		preparation.addLines(intake.source(), intake.sourceId(), java.util.List.of(aLine()));
		commands.acknowledge(PreparationTables.markerId(jdbc, order, "MODIFIED"), UUID.randomUUID());

		var card = commands.readyOrder(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.READY);
		assertThat(card.lines()).allSatisfy(line -> assertThat(line.state()).isEqualTo(BoardLineState.READY));
	}

	@Test
	void anUnacknowledgedMarkerOfALineAlreadyReadyDoesNotBlockTheWholeOrder() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var extra = aLine();
		preparation.addLines(intake.source(), intake.sourceId(), java.util.List.of(extra));
		commands.acknowledge(PreparationTables.markerId(jdbc, order, "MODIFIED"), UUID.randomUUID());
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, extra.sourceLineId()));

		var card = commands.readyOrder(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.READY);
	}

	@Test
	void theWholeOrderCannotBeReadyBeforeItIsStarted() {
		var order = accepted(aLine());

		assertRejected(() -> commands.readyOrder(order), PreparationError.ORDER_NOT_PREPARING);
	}

	@Test
	void handingOverAReadyOrderCompletesIt() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);

		var card = commands.handover(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.HANDED_OVER);
		assertThat(card.handedOverAt()).isNotNull();
		assertThat(events.stream(OrderHandedOver.class)).hasSize(1);
	}

	@Test
	void anOrderThatIsNotReadyCannotBeHandedOver() {
		var order = accepted(aLine());
		commands.start(order);

		assertRejected(() -> commands.handover(order), PreparationError.ORDER_NOT_READY);
	}

	@Test
	void handingOverTwiceRejectsTheSecondHandover() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);
		commands.handover(order);

		assertRejected(() -> commands.handover(order), PreparationError.ORDER_NOT_READY);
	}

	@Test
	void recallingAReadyOrderPutsItBackToPreparingWithItsLinesPending() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		commands.readyOrder(order);

		var card = commands.recall(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.PREPARING);
		assertThat(card.readyAt()).isNull();
		assertThat(card.lines()).allSatisfy(line -> assertThat(line.state()).isEqualTo(BoardLineState.PENDING));
		assertThat(events.stream(OrderRecalled.class)).hasSize(1);
	}

	@Test
	void recallingAHandedOverOrderWithinTheWindowBringsItBack() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);
		commands.handover(order);

		var card = commands.recall(order);

		assertThat(card.status()).isEqualTo(BoardOrderStatus.PREPARING);
		assertThat(card.handedOverAt()).isNull();
	}

	@Test
	void anOrderCompletedLongAgoCannotBeRecalled() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);
		commands.handover(order);
		var longAgo = Instant.now().minus(31, ChronoUnit.MINUTES);
		PreparationTables.completedAt(jdbc, order, longAgo, longAgo);

		assertRejected(() -> commands.recall(order), PreparationError.RECALL_WINDOW_EXPIRED);
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("HANDED_OVER");
	}

	@Test
	void theRecallWindowRunsFromTheHandoverNotFromTheMomentItWasReady() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);
		commands.handover(order);
		PreparationTables.completedAt(jdbc, order, Instant.now().minus(50, ChronoUnit.MINUTES),
				Instant.now().minus(5, ChronoUnit.MINUTES));

		assertThat(commands.recall(order).status()).isEqualTo(BoardOrderStatus.PREPARING);
	}

	@Test
	void anOrderStillBeingPreparedCannotBeRecalled() {
		var order = accepted(aLine());
		commands.start(order);

		assertRejected(() -> commands.recall(order), PreparationError.ORDER_NOT_COMPLETED);
	}

	@Test
	void flaggingALineMarksItUnavailableAndPublishesIt() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		var line = lines(order).getFirst();

		var card = commands.flagLine(line);

		assertThat(card.lines().getFirst().flaggedUnavailable()).isTrue();
		assertThat(events.stream(LineFlaggedUnavailable.class)).singleElement()
			.satisfies(event -> assertThat(event.lineId()).isEqualTo(line));
		assertThat(changes()).contains("LINE_FLAGGED");
	}

	@Test
	void aLineCanBeFlaggedBeforeTheOrderIsStarted() {
		var order = accepted(aLine());

		assertThat(commands.flagLine(lines(order).getFirst()).lines().getFirst().flaggedUnavailable()).isTrue();
	}

	@Test
	void flaggingTwiceRejectsTheSecondFlag() {
		var order = accepted(aLine());
		var line = lines(order).getFirst();
		commands.flagLine(line);

		assertRejected(() -> commands.flagLine(line), PreparationError.LINE_ALREADY_FLAGGED);
	}

	@Test
	void aLineThatIsAlreadyReadyCannotBeFlagged() {
		var order = accepted(aLine(), aLine());
		commands.start(order);
		var line = lines(order).getFirst();
		commands.readyLine(line);

		assertRejected(() -> commands.flagLine(line), PreparationError.LINE_NOT_PENDING);
	}

	@Test
	void aCompedLineCannotBeFlaggedUnavailable() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);
		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());
		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());

		assertRejected(() -> commands.flagLine(line), PreparationError.LINE_COMPED);
		assertThat(queries.card(order).lines()).anySatisfy(l -> assertThat(l.flaggedUnavailable()).isFalse());
	}

	@Test
	void aLineOfAReadyOrderCannotBeFlagged() {
		var order = accepted(aLine());
		commands.start(order);
		commands.readyOrder(order);

		assertRejected(() -> commands.flagLine(lines(order).getFirst()), PreparationError.LINE_NOT_PENDING);
	}

	@Test
	void acknowledgingAMarkerClearsItFromTheCard() {
		var order = accepted(aLine());
		var actor = UUID.randomUUID();
		var marker = PreparationTables.markerId(jdbc, order, "NEW");
		assertThat(queries.card(order).markers()).hasSize(1);

		var acknowledged = commands.acknowledge(marker, actor);

		assertThat(acknowledged.acknowledgedAt()).isNotNull();
		assertThat(queries.card(order).markers()).isEmpty();
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW");
		assertThat(jdbc.sql("select acknowledged_by from preparation.board_marker where id = ?")
			.param(marker)
			.query(UUID.class)
			.single()).isEqualTo(actor);
		assertThat(changes()).contains("MARKER_ACKNOWLEDGED");
	}

	@Test
	void acknowledgingTwiceRejectsTheSecondAcknowledgement() {
		var order = accepted(aLine());
		var marker = PreparationTables.markerId(jdbc, order, "NEW");
		commands.acknowledge(marker, UUID.randomUUID());

		assertRejected(() -> commands.acknowledge(marker, UUID.randomUUID()),
				PreparationError.MARKER_ALREADY_ACKNOWLEDGED);
	}

	@Test
	void acknowledgingAnUnknownMarkerIsNotFound() {
		assertRejected(() -> commands.acknowledge(UUID.randomUUID(), UUID.randomUUID()),
				PreparationError.MARKER_NOT_FOUND);
	}

	private UUID accepted(com.jhanantezana.jugueria.preparation.BoardLineIntake... lines) {
		BoardOrderIntake intake = anOrder(lines);
		return preparation.accept(intake);
	}

	private java.util.List<UUID> lines(UUID order) {
		return PreparationTables.lineIds(jdbc, order);
	}

	private java.util.List<String> changes() {
		return events.stream(BoardChanged.class).map(BoardChanged::type).toList();
	}

	private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
			PreparationError error) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(error));
	}

}
