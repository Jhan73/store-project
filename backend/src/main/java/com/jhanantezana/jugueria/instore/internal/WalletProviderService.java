package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.WalletProviderChangeKind;
import com.jhanantezana.jugueria.instore.WalletProviderChanged;
import com.jhanantezana.jugueria.instore.WalletProviderSnapshot;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class WalletProviderService {

	private final WalletProviderRepository providers;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	WalletProviderService(WalletProviderRepository providers, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.providers = providers;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<WalletProvider> list() {
		return providers.findAllByOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	public List<WalletProvider> listActive() {
		return providers.findAllByActiveTrueOrderByNameAsc();
	}

	@Transactional
	public WalletProvider create(String name) {
		var now = Instant.now(clock);
		var provider = new WalletProvider(name, now);
		flushing(() -> providers.saveAndFlush(provider));
		publish(provider, WalletProviderChangeKind.CREATED, null, now);
		return provider;
	}

	@Transactional
	public WalletProvider rename(UUID id, String name, long expectedVersion) {
		var provider = find(id, expectedVersion);
		var before = provider.snapshot();
		var now = Instant.now(clock);
		provider.rename(name, now);
		flushing(providers::flush);
		publish(provider, WalletProviderChangeKind.UPDATED, before, now);
		return provider;
	}

	@Transactional
	public WalletProvider deactivate(UUID id, long expectedVersion) {
		var provider = providers.findForUpdateById(id).orElseThrow(WalletProviderService::notFound);
		EntityVersions.requireMatching(provider.getVersion(), expectedVersion);
		if (provider.isActive()) {
			var before = provider.snapshot();
			var now = Instant.now(clock);
			provider.deactivate(now);
			providers.flush();
			publish(provider, WalletProviderChangeKind.DEACTIVATED, before, now);
		}
		return provider;
	}

	@Transactional
	public WalletProvider reactivate(UUID id, long expectedVersion) {
		var provider = find(id, expectedVersion);
		if (!provider.isActive()) {
			var before = provider.snapshot();
			var now = Instant.now(clock);
			provider.reactivate(now);
			flushing(providers::flush);
			publish(provider, WalletProviderChangeKind.ACTIVATED, before, now);
		}
		return provider;
	}

	/**
	 * The provider a payment names: required, and active, for a wallet payment; absent for any other method.
	 * Joins the payment's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public @Nullable WalletProviderRef requireFor(PaymentMethod method, @Nullable UUID providerId) {
		if (method != PaymentMethod.WALLET) {
			if (providerId != null) {
				throw new BusinessException(InstoreError.WALLET_PROVIDER_NOT_ALLOWED,
						"Only a wallet payment names a wallet provider");
			}
			return null;
		}
		if (providerId == null) {
			throw new BusinessException(InstoreError.WALLET_PROVIDER_REQUIRED, "A wallet payment names its provider");
		}
		var provider = providers.findForShareById(providerId).orElseThrow(WalletProviderService::notFound);
		if (!provider.isActive()) {
			throw new BusinessException(InstoreError.WALLET_PROVIDER_INACTIVE, "The wallet provider is deactivated");
		}
		return new WalletProviderRef(provider.getId(), provider.getName());
	}

	private WalletProvider find(UUID id, long expectedVersion) {
		var provider = providers.findById(id).orElseThrow(WalletProviderService::notFound);
		EntityVersions.requireMatching(provider.getVersion(), expectedVersion);
		return provider;
	}

	private void publish(WalletProvider provider, WalletProviderChangeKind kind,
			@Nullable WalletProviderSnapshot before, Instant now) {
		events.publishEvent(new WalletProviderChanged(provider.getId(), kind, before, provider.snapshot(),
				currentActor.id(), currentActor.role(), now));
	}

	private static BusinessException notFound() {
		return new BusinessException(InstoreError.WALLET_PROVIDER_NOT_FOUND, "Wallet provider not found");
	}

	private static void flushing(Runnable write) {
		try {
			write.run();
		}
		catch (DataIntegrityViolationException e) {
			if (Constraints.violated(e, Constraints.WALLET_PROVIDER_NAME)) {
				throw new BusinessException(InstoreError.WALLET_PROVIDER_NAME_ALREADY_USED,
						"An active wallet provider with this name already exists");
			}
			throw e;
		}
	}

}
