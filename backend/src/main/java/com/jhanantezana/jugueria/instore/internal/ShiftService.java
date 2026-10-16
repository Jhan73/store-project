package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.ShiftOpened;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.StoreApi;

import org.springframework.data.domain.Page;

/** Opening and reading register shifts; closing them lives in {@link ShiftClosing}. */
@Service
public class ShiftService {

	private final Shifts shifts;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	ShiftService(Shifts shifts, StoreApi store, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.shifts = shifts;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional
	public ShiftView open(Money openingFloat) {
		var floatAmount = Amounts.require(openingFloat, store.currency(), true, "The opening float");
		var cashier = requireActor();
		var now = Instant.now(clock);
		var id = Ids.newId();
		if (!shifts.insert(id, cashier, floatAmount.currency(), floatAmount.amount(), now)) {
			throw new BusinessException(InstoreError.SHIFT_ALREADY_OPEN, "The cashier already has an open shift");
		}
		events.publishEvent(new ShiftOpened(id, cashier, floatAmount, cashier, currentActor.role(), now));
		return view(shifts.find(id).orElseThrow());
	}

	@Transactional(readOnly = true)
	public ShiftView current() {
		return shifts.findOpenOf(requireActor())
			.map(ShiftService::view)
			.orElseThrow(() -> new BusinessException(InstoreError.SHIFT_NOT_FOUND, "There is no open shift"));
	}

	/** A cashier reads only their own shifts; to anyone else a shift that is not theirs does not exist. */
	@Transactional(readOnly = true)
	public ShiftView get(UUID id) {
		var actor = requireActor();
		return shifts.find(id)
			.filter(shift -> shift.cashierId().equals(actor) || currentActor.role() == Role.ADMIN)
			.map(ShiftService::view)
			.orElseThrow(() -> notFound());
	}

	@Transactional(readOnly = true)
	public Page<ShiftView> list(@Nullable ShiftStatus status, Pageable pageable) {
		var rows = shifts.page(status, pageable.getPageSize(), pageable.getOffset()).stream().map(ShiftService::view).toList();
		return new PageImpl<>(rows, pageable, shifts.count(status));
	}

	static ShiftView view(Shifts.ShiftRow shift) {
		var closed = shift.status() == ShiftStatus.CLOSED;
		var currency = shift.currency();
		return new ShiftView(shift.id(), shift.cashierId(), shift.status(), shift.floatMoney(), shift.openedAt(),
				shift.closedAt(), shift.closeKind(), closed && shift.counted() != null ? new Money(shift.counted(), currency) : null,
				closed ? new Money(shift.expected(), currency) : null,
				closed && shift.difference() != null ? new Money(shift.difference(), currency) : null,
				shift.differenceNote(), shift.forceReason(), shift.closedBy(), shift.totals());
	}

	static BusinessException notFound() {
		return new BusinessException(InstoreError.SHIFT_NOT_FOUND, "Shift not found");
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A shift command needs a signed-in user");
		}
		return id;
	}

}
