package com.jhanantezana.jugueria.preparation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * How the sales modules feed the board and take part in its races. Every method joins the caller's transaction, so
 * a ticket change and its board change commit or roll back together. Sending the same order or line twice changes nothing the second time.
 *
 * <p>
 * Rejections are {@code BusinessException}s with a {@link PreparationError} code. The ones a caller is expected to
 * translate: {@code preparation.order-already-started} when {@link #cancel} loses to staff starting the order, and
 * {@code preparation.line-already-ready} when a {@link VoidMode#PENDING_ONLY} void loses to staff finishing the line.
 */
public interface PreparationApi {

	/**
	 * Puts a paid order or a sent ticket on the board and returns the card's id. A source already on the board is left
	 * as it is and its existing card id returned.
	 */
	UUID accept(BoardOrderIntake intake);

	/**
	 * Adds lines to a card that is not finished, labelling them MODIFIED. A card that was ready goes back to
	 * preparing. Lines already on the card (by {@code sourceLineId}) are skipped. A handed-over card is closed to new
	 * lines ({@code preparation.order-closed}), a cancelled one is {@code preparation.order-cancelled}.
	 */
	void addLines(BoardSource source, UUID sourceId, List<BoardLineIntake> lines);

	/**
	 * Like {@link #addLines}, but a card that is handed over, cancelled or gone answers {@code false} instead of failing, so
	 * the caller decides on the card's state and adds the lines in one atomic step. Answers {@code true} when the lines
	 * are on the card afterwards, including when they already were.
	 */
	boolean tryAddLines(BoardSource source, UUID sourceId, List<BoardLineIntake> lines);

	/**
	 * Voids a line, labelling it VOID. A finished line is voided only with {@link VoidMode#ALLOW_READY_AS_WASTE} and
	 * then counts as waste; otherwise the void is rejected with {@code preparation.line-already-ready}. Voiding a line
	 * that is already voided does nothing. When no line is left to prepare the card is cancelled, and when the voided
	 * line was the last one still pending the card becomes ready. A handed-over card still accepts a waste void, since
	 * the customer can reject what was handed over; only a cancelled card is closed.
	 *
	 * @return whether the line counts as waste, also when it was voided before
	 */
	boolean voidLine(BoardSource source, UUID sourceId, UUID sourceLineId, VoidMode mode);

	/**
	 * Comps a line: served free of charge, so it counts as waste. A pending or finished line becomes COMPED, with no
	 * marker; comping a line that is already comped does nothing, and a voided one is
	 * {@code preparation.line-not-pending}. When no line is left to prepare the card is cancelled, and when the comped
	 * line was the last one still pending the card becomes ready. A handed-over card still accepts a comp; only a
	 * cancelled card is closed.
	 */
	void compLine(BoardSource source, UUID sourceId, UUID sourceLineId);

	/**
	 * Cancels a card nobody has started. Cancelling a card that is already cancelled does nothing; one that staff
	 * started is {@code preparation.order-already-started}.
	 */
	void cancel(BoardSource source, UUID sourceId);

	/**
	 * Changes the label of the card the source feeds (the table a ticket moved to), whatever its status. A source
	 * with no card, or a card that already has the label, is left alone.
	 */
	void relabel(BoardSource source, UUID sourceId, String label);

	/** The card's status, or empty when the source never reached the board. */
	Optional<BoardOrderStatus> statusOf(BoardSource source, UUID sourceId);

}
