package com.jhanantezana.jugueria.preparation;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class PreparationIntakeIT {

	@Autowired
	PreparationApi preparation;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@Test
	void acceptingAnOrderPutsItOnTheBoardWaitingWithItsLinesPending() {
		var first = aLine();
		var second = aLine();
		var intake = anOrder(first, second);

		var id = preparation.accept(intake);

		assertThat(PreparationTables.orderStatus(jdbc, id)).isEqualTo("WAITING");
		assertThat(PreparationTables.lineIds(jdbc, id)).hasSize(2)
			.allSatisfy(line -> assertThat(PreparationTables.lineState(jdbc, line)).isEqualTo("PENDING"));
		assertThat(preparation.statusOf(intake.source(), intake.sourceId())).contains(BoardOrderStatus.WAITING);
		assertThat(PreparationTables.markers(jdbc, id)).containsExactly("NEW*");
		assertThat(events.stream(BoardChanged.class)).singleElement().satisfies(event -> {
			assertThat(event.type()).isEqualTo("ORDER_ADDED");
			assertThat(event.boardOrderId()).isEqualTo(id);
		});
	}

	@Test
	void acceptingTheSameSourceTwiceKeepsTheFirstCard() {
		var intake = anOrder(aLine());

		var first = preparation.accept(intake);
		var second = preparation.accept(anOrder(intake.sourceId(), aLine(), aLine()));

		assertThat(second).isEqualTo(first);
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(PreparationTables.lineIds(jdbc, first)).hasSize(1);
		assertThat(PreparationTables.markers(jdbc, first)).containsExactly("NEW*");
		assertThat(events.stream(BoardChanged.class)).hasSize(1);
	}

	@Test
	void sameOptionsInAnyOrderShareASignatureAndDifferentOptionsDoNot() {
		var honey = UUID.randomUUID();
		var ginger = UUID.randomUUID();
		var product = UUID.randomUUID();
		var id = preparation.accept(anOrder(PreparationTables.aLine(product, "Juice", 1, List.of(honey, ginger)),
				PreparationTables.aLine(product, "Juice", 1, List.of(ginger, honey)),
				PreparationTables.aLine(product, "Juice", 1, List.of(honey))));

		var signatures = jdbc.sql("select modifier_signature from preparation.board_line where board_order_id = ? "
				+ "order by created_at, id").param(id).query(String.class).list();

		assertThat(signatures.get(0)).isEqualTo(signatures.get(1)).isNotEqualTo(signatures.get(2));
	}

	@Test
	void addingLinesLabelsThemModifiedAndSkipsLinesAlreadyThere() {
		var existing = aLine();
		var intake = anOrder(existing);
		var id = preparation.accept(intake);
		var added = aLine();

		preparation.addLines(intake.source(), intake.sourceId(), List.of(existing, added));
		preparation.addLines(intake.source(), intake.sourceId(), List.of(added));

		assertThat(PreparationTables.lineIds(jdbc, id)).hasSize(2);
		assertThat(PreparationTables.markers(jdbc, id)).containsExactly("NEW*", "MODIFIED*");
		assertThat(events.stream(BoardChanged.class).map(BoardChanged::type)).containsExactly("ORDER_ADDED",
				"LINES_ADDED");
	}

	@Test
	void addingLinesToAReadyOrderSendsItBackToPreparing() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, id, "READY");

		preparation.addLines(intake.source(), intake.sourceId(), List.of(aLine()));

		assertThat(PreparationTables.orderStatus(jdbc, id)).isEqualTo("PREPARING");
	}

	@Test
	void rejectsAddingLinesToAnOrderThatIsNotOnTheBoard() {
		assertThatThrownBy(() -> preparation.addLines(BoardSource.TICKET, UUID.randomUUID(), List.of(aLine())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_NOT_FOUND));
	}

	@Test
	void rejectsAddingLinesToAHandedOverOrder() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, id, "HANDED_OVER");

		assertThatThrownBy(() -> preparation.addLines(intake.source(), intake.sourceId(), List.of(aLine())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_CLOSED));
	}

	@Test
	void rejectsAddingLinesToACancelledOrder() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, id, "CANCELLED");

		assertThatThrownBy(() -> preparation.addLines(intake.source(), intake.sourceId(), List.of(aLine())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_CANCELLED));
	}

	@Test
	void redeliveringLinesAlreadyAddedIsANoOpEvenOnceTheCardIsHandedOver() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		var added = aLine();
		preparation.addLines(intake.source(), intake.sourceId(), List.of(added));
		PreparationTables.forceStatus(jdbc, id, "HANDED_OVER");

		preparation.addLines(intake.source(), intake.sourceId(), List.of(added));

		assertThat(PreparationTables.lineIds(jdbc, id)).hasSize(2);
		assertThat(PreparationTables.orderStatus(jdbc, id)).isEqualTo("HANDED_OVER");
	}

	@Test
	void redeliveringLinesAlreadyAddedIsANoOpEvenOnceTheCardIsCancelled() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		var added = aLine();
		preparation.addLines(intake.source(), intake.sourceId(), List.of(added));
		PreparationTables.forceStatus(jdbc, id, "CANCELLED");

		preparation.addLines(intake.source(), intake.sourceId(), List.of(added));

		assertThat(PreparationTables.lineIds(jdbc, id)).hasSize(2);
	}

	@Test
	void aSendMixingKnownAndNewLinesIsStillRejectedOnAHandedOverCard() {
		var known = aLine();
		var intake = anOrder(known);
		var id = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, id, "HANDED_OVER");

		assertThatThrownBy(() -> preparation.addLines(intake.source(), intake.sourceId(), List.of(known, aLine())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(PreparationError.ORDER_CLOSED));
		assertThat(PreparationTables.lineIds(jdbc, id)).hasSize(1);
	}

	@Test
	void acceptingASourceWhoseCardWasCancelledReturnsTheCardWithoutBringingItBack() {
		var intake = anOrder(aLine());
		var id = preparation.accept(intake);
		PreparationTables.forceStatus(jdbc, id, "CANCELLED");

		var again = preparation.accept(intake);

		assertThat(again).isEqualTo(id);
		assertThat(PreparationTables.orderStatus(jdbc, id)).isEqualTo("CANCELLED");
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(events.stream(BoardChanged.class)).hasSize(1);
	}

	@Test
	void reportsNothingForASourceThatNeverReachedTheBoard() {
		assertThat(preparation.statusOf(BoardSource.ONLINE, UUID.randomUUID())).isEmpty();
	}

}
