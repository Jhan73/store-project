package com.jhanantezana.jugueria.store.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Singletons;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;

@Service
public class StoreSettingsService {

	private final StoreSettingsRepository settings;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	StoreSettingsService(StoreSettingsRepository settings, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.settings = settings;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public StoreSettings get() {
		return singleton();
	}

	// Short-circuits the common case; Hibernate's own version-guarded UPDATE at flush is the real race guard.
	@Transactional
	public StoreSettings update(String timeZone, Currency currency, int basePrepMinutes, int queueMinutesPerOrder,
			int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes, Money registerDifferenceThreshold,
			int exceptionThreshold, int onlineCapacityLimit, long expectedVersion) {
		var current = singleton();
		if (current.getVersion() != expectedVersion) {
			throw preconditionFailed(current.getVersion());
		}
		var before = current.snapshot();
		var now = Instant.now(clock);
		current.update(timeZone, currency, basePrepMinutes, queueMinutesPerOrder, busyModeMinutes, boardWarningMinutes,
				boardLateMinutes, registerDifferenceThreshold, exceptionThreshold, onlineCapacityLimit, now);
		var after = current.snapshot();
		events.publishEvent(
				new StoreSettingsChanged(current.getId(), before, after, currentActor.id(), currentActor.role(), now));
		return current;
	}

	private StoreSettings singleton() {
		return Singletons.requireOne(settings.findAll());
	}

	private static BusinessException preconditionFailed(long currentVersion) {
		return new BusinessException(CommonError.PRECONDITION_FAILED, "If-Match does not match the current ETag",
				Map.of("currentETag", ETags.format(currentVersion)));
	}

}
