package com.jhanantezana.jugueria.notifications.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;

// Test-only: records what the actor looks like from inside an after-commit listener's own thread.
@TestComponent
class CurrentActorProbe {

	private final CurrentActor currentActor;

	private final CompletableFuture<Boolean> sawSystemActor = new CompletableFuture<>();

	CurrentActorProbe(CurrentActor currentActor) {
		this.currentActor = currentActor;
	}

	@Async
	@TransactionalEventListener
	void on(StoreSettingsChanged event) {
		sawSystemActor.complete(currentActor.isSystem());
	}

	boolean awaitSawSystemActor() throws Exception {
		return sawSystemActor.get(5, TimeUnit.SECONDS);
	}

}
