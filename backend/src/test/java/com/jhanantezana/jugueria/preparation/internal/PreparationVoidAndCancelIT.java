package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.OrderReady;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class PreparationVoidAndCancelIT {

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@Test
	void voidingAPendingLineLabelsItVoidAndKeepsItOnTheCardUntilAcknowledged() {
		var first = aLine();
		var second = aLine();
		var intake = anOrder(first, second);
		var order = preparation.accept(intake);

		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY);

		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("VOIDED");
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "VOID*");
		assertThat(events.stream(BoardChanged.class).map(BoardChanged::type)).contains("LINE_VOIDED");
		var card = card(order);
		assertThat(card.lines()).hasSize(2);
		commands.acknowledge(PreparationTables.markerId(jdbc, order, "VOID"), UUID.randomUUID());
		assertThat(card(order).lines()).hasSize(1);
	}

	@Test
	void aPendingOnlyVoidIsRejectedOnceTheLineIsReady() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		commands.readyLine(line);

		assertThatThrownBy(
				() -> preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_ALREADY_READY));
		assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("READY");
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*");
	}

	@Test
	void voidingAReadyLineAsWasteKeepsItCounted() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		commands.readyLine(line);

		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.ALLOW_READY_AS_WASTE);

		assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("VOIDED");
		assertThat(jdbc.sql("select waste from preparation.board_line where id = ?")
			.param(line)
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void aVoidedPendingLineIsNotWaste() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);

		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.ALLOW_READY_AS_WASTE);

		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		assertThat(jdbc.sql("select waste from preparation.board_line where id = ?")
			.param(line)
			.query(Boolean.class)
			.single()).isFalse();
	}

	@Test
	void voidingTheLastPendingLineOfAPreparingOrderMakesItReady() {
		var pending = aLine();
		var done = aLine();
		var intake = anOrder(done, pending);
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, done.sourceLineId()));

		preparation.voidLine(intake.source(), intake.sourceId(), pending.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("READY");
		assertThat(events.stream(OrderReady.class)).hasSize(1);
	}

	@Test
	void voidingEveryLineCancelsTheOrder() {
		var first = aLine();
		var second = aLine();
		var intake = anOrder(first, second);
		var order = preparation.accept(intake);

		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY);
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("WAITING");
		preparation.voidLine(intake.source(), intake.sourceId(), second.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
		assertThat(preparation.statusOf(intake.source(), intake.sourceId())).contains(BoardOrderStatus.CANCELLED);
	}

	@Test
	void voidingAFlaggedLineResolvesItAndLetsTheOrderFinish() {
		var flagged = aLine();
		var other = aLine();
		var intake = anOrder(flagged, other);
		var order = preparation.accept(intake);
		commands.start(order);
		var flaggedId = PreparationTables.lineIdOf(jdbc, order, flagged.sourceLineId());
		commands.flagLine(flaggedId);

		preparation.voidLine(intake.source(), intake.sourceId(), flagged.sourceLineId(), VoidMode.PENDING_ONLY);
		commands.readyOrder(order);

		assertThat(jdbc.sql("select resolution from preparation.board_line where id = ?")
			.param(flaggedId)
			.query(String.class)
			.single()).isEqualTo("VOIDED");
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("READY");
	}

	@Test
	void voidingALineTwiceChangesNothingTheSecondTime() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);

		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY);
		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "VOID*");
	}

	@Test
	void retryingTheVoidThatCancelledTheCardChangesNothing() {
		var only = aLine();
		var intake = anOrder(only);
		var order = preparation.accept(intake);
		preparation.voidLine(intake.source(), intake.sourceId(), only.sourceLineId(), VoidMode.PENDING_ONLY);
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");

		preparation.voidLine(intake.source(), intake.sourceId(), only.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "VOID*");
		assertThat(events.stream(BoardChanged.class).filter(e -> e.type().equals("LINE_VOIDED"))).hasSize(1);
	}

	@Test
	void retryingAVoidOnceTheCardIsHandedOverChangesNothing() {
		var done = aLine();
		var voided = aLine();
		var intake = anOrder(done, voided);
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, done.sourceLineId()));
		preparation.voidLine(intake.source(), intake.sourceId(), voided.sourceLineId(), VoidMode.PENDING_ONLY);
		commands.handover(order);

		preparation.voidLine(intake.source(), intake.sourceId(), voided.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("HANDED_OVER");
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*", "VOID*");
	}

	@Test
	void voidingAnUnknownLineOnAClosedCardIsLineNotFound() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, order, "CANCELLED");

		assertThatThrownBy(() -> preparation.voidLine(intake.source(), intake.sourceId(), UUID.randomUUID(),
				VoidMode.PENDING_ONLY)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_NOT_FOUND));
		PreparationTables.forceStatus(jdbc, order, "HANDED_OVER");
		assertThatThrownBy(() -> preparation.voidLine(intake.source(), intake.sourceId(), UUID.randomUUID(),
				VoidMode.PENDING_ONLY)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_NOT_FOUND));
	}

	@Test
	void voidingAnUnknownLineOrOrderIsNotFound() {
		var intake = anOrder(aLine());
		preparation.accept(intake);

		assertThatThrownBy(() -> preparation.voidLine(intake.source(), intake.sourceId(), UUID.randomUUID(),
				VoidMode.PENDING_ONLY)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_NOT_FOUND));
		assertThatThrownBy(() -> preparation.voidLine(intake.source(), UUID.randomUUID(), UUID.randomUUID(),
				VoidMode.PENDING_ONLY)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_NOT_FOUND));
	}

	@Test
	void aHandedOverOrderIsClosedToVoids() {
		var line = aLine();
		var intake = anOrder(line);
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyOrder(order);
		commands.handover(order);

		assertThatThrownBy(() -> preparation.voidLine(intake.source(), intake.sourceId(), line.sourceLineId(),
				VoidMode.ALLOW_READY_AS_WASTE)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_CLOSED));
	}

	@Test
	void cancellingAWaitingOrderCancelsItsCard() {
		var intake = anOrder(aLine());
		var order = preparation.accept(intake);

		preparation.cancel(intake.source(), intake.sourceId());

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
		assertThat(events.stream(BoardChanged.class).map(BoardChanged::type)).contains("ORDER_CANCELLED");
	}

	@Test
	void cancellingAnOrderStaffAlreadyStartedIsRejected() {
		BoardOrderIntake intake = anOrder(aLine());
		var order = preparation.accept(intake);
		commands.start(order);

		assertThatThrownBy(() -> preparation.cancel(intake.source(), intake.sourceId()))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_ALREADY_STARTED));
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("PREPARING");
	}

	@Test
	void cancellingTwiceChangesNothingTheSecondTime() {
		var intake = anOrder(aLine());
		preparation.accept(intake);

		preparation.cancel(intake.source(), intake.sourceId());
		preparation.cancel(intake.source(), intake.sourceId());

		assertThat(events.stream(BoardChanged.class).filter(event -> event.type().equals("ORDER_CANCELLED"))).hasSize(1);
	}

	@Test
	void cancellingAnOrderThatNeverReachedTheBoardIsNotFound() {
		assertThatThrownBy(() -> preparation.cancel(anOrder(aLine()).source(), UUID.randomUUID()))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_NOT_FOUND));
	}

	@Autowired
	BoardQueries queries;

	private BoardOrderView card(UUID order) {
		return queries.card(order);
	}

}
