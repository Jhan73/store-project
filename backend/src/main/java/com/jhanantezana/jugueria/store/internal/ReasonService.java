package com.jhanantezana.jugueria.store.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.ReasonCreated;
import com.jhanantezana.jugueria.store.ReasonStatusChanged;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreError;

@Service
public class ReasonService {

	private final ReasonRepository reasons;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	ReasonService(ReasonRepository reasons, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.reasons = reasons;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Reason> byType(ReasonType type) {
		return reasons.findByType(type);
	}

	@Transactional
	public Reason create(ReasonType type, String code) {
		var now = Instant.now(clock);
		var reason = new Reason(type, code, now);
		try {
			reasons.saveAndFlush(reason);
		}
		catch (DataIntegrityViolationException e) {
			throw codeAlreadyUsed();
		}
		events.publishEvent(new ReasonCreated(reason.getId(), type, code, currentActor.id(), currentActor.role(), now));
		return reason;
	}

	@Transactional
	public Reason deactivate(UUID id) {
		var reason = findOrThrow(id);
		if (!reason.isActive()) {
			return reason;
		}
		var now = Instant.now(clock);
		reason.deactivate(now);
		events.publishEvent(new ReasonStatusChanged(reason.getId(), false, currentActor.id(), currentActor.role(), now));
		return reason;
	}

	@Transactional
	public Reason reactivate(UUID id) {
		var reason = findOrThrow(id);
		if (reason.isActive()) {
			return reason;
		}
		var now = Instant.now(clock);
		reason.reactivate(now);
		events.publishEvent(new ReasonStatusChanged(reason.getId(), true, currentActor.id(), currentActor.role(), now));
		return reason;
	}

	private Reason findOrThrow(UUID id) {
		return reasons.findById(id).orElseThrow(ReasonService::notFound);
	}

	private static BusinessException notFound() {
		return new BusinessException(StoreError.REASON_NOT_FOUND, "Reason not found");
	}

	private static BusinessException codeAlreadyUsed() {
		return new BusinessException(StoreError.REASON_CODE_ALREADY_USED,
				"A reason with this code already exists for this type");
	}

}
