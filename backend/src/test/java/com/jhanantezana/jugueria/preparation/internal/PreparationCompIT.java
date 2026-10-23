package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.jhanantezana.jugueria.preparation.OrderReady;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class PreparationCompIT {

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
	void compingAReadyLineMarksItCompedAsWasteWithoutAMarker() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);
		commands.start(order);
		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		commands.readyLine(line);

		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());

		assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("COMPED");
		assertThat(waste(line)).isTrue();
		assertThat(PreparationTables.markers(jdbc, order)).containsExactly("NEW*");
		assertThat(events.stream(BoardChanged.class).map(BoardChanged::type)).contains("LINE_COMPED");
	}

	@Test
	void compingAPendingLineIsAllowedAndCountsAsWaste() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);

		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());

		var line = PreparationTables.lineIdOf(jdbc, order, first.sourceLineId());
		assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("COMPED");
		assertThat(waste(line)).isTrue();
	}

	@Test
	void compingTheLastPendingLineOfAPreparingOrderMakesItReady() {
		var pending = aLine();
		var done = aLine();
		var intake = anOrder(done, pending);
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, done.sourceLineId()));

		preparation.compLine(intake.source(), intake.sourceId(), pending.sourceLineId());

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("READY");
		assertThat(events.stream(OrderReady.class)).hasSize(1);
	}

	@Test
	void compingTheOnlyLiveLineCancelsTheOrder() {
		var only = aLine();
		var intake = anOrder(only);
		var order = preparation.accept(intake);

		preparation.compLine(intake.source(), intake.sourceId(), only.sourceLineId());

		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("CANCELLED");
	}

	@Test
	void compingALineTwiceChangesNothing() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		var order = preparation.accept(intake);
		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());
		var changes = events.stream(BoardChanged.class).filter(change -> change.type().equals("LINE_COMPED")).count();

		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());

		assertThat(events.stream(BoardChanged.class).filter(change -> change.type().equals("LINE_COMPED")).count())
			.isEqualTo(changes);
		assertThat(PreparationTables.lineState(jdbc, PreparationTables.lineIdOf(jdbc, order, first.sourceLineId())))
			.isEqualTo("COMPED");
	}

	@Test
	void aHandedOverCardStillAcceptsAComp() {
		var first = aLine();
		var intake = anOrder(first);
		var order = preparation.accept(intake);
		commands.start(order);
		commands.readyLine(PreparationTables.lineIdOf(jdbc, order, first.sourceLineId()));
		PreparationTables.forceStatus(jdbc, order, "HANDED_OVER");

		preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId());

		assertThat(PreparationTables.lineState(jdbc, PreparationTables.lineIdOf(jdbc, order, first.sourceLineId())))
			.isEqualTo("COMPED");
		assertThat(PreparationTables.orderStatus(jdbc, order)).isEqualTo("HANDED_OVER");
	}

	@Test
	void aVoidedLineCannotBeComped() {
		var first = aLine();
		var intake = anOrder(first, aLine());
		preparation.accept(intake);
		preparation.voidLine(intake.source(), intake.sourceId(), first.sourceLineId(), VoidMode.PENDING_ONLY);

		assertThatThrownBy(() -> preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId()))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_NOT_PENDING));
	}

	@Test
	void aCancelledCardRejectsAComp() {
		var first = aLine();
		var intake = anOrder(first);
		preparation.accept(intake);
		preparation.cancel(intake.source(), intake.sourceId());

		assertThatThrownBy(() -> preparation.compLine(intake.source(), intake.sourceId(), first.sourceLineId()))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_CANCELLED));
	}

	@Test
	void anUnknownLineIsNotFound() {
		var intake = anOrder(aLine());
		preparation.accept(intake);

		assertThatThrownBy(() -> preparation.compLine(intake.source(), intake.sourceId(), java.util.UUID.randomUUID()))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.LINE_NOT_FOUND));
	}

	private boolean waste(java.util.UUID line) {
		return jdbc.sql("select waste from preparation.board_line where id = ?")
			.param(line)
			.query(Boolean.class)
			.single();
	}

}
