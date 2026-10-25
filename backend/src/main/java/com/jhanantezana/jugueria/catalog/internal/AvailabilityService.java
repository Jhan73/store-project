package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.catalog.AvailabilityTarget;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ProductAvailabilityGuard;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

// The "86": no If-Match, because setting an absolute value is idempotent and staff act on a live screen.
@Service
public class AvailabilityService {

	private final ProductRepository products;

	private final ModifierOptionRepository options;

	private final CatalogChanges changes;

	private final List<ProductAvailabilityGuard> guards;

	private final CurrentActor currentActor;

	private final Clock clock;

	AvailabilityService(ProductRepository products, ModifierOptionRepository options, CatalogChanges changes,
			List<ProductAvailabilityGuard> guards, CurrentActor currentActor, Clock clock) {
		this.products = products;
		this.options = options;
		this.changes = changes;
		this.guards = guards;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** The 86 screen's command: marking a product available asks every guard first, unlike the system's own flips. */
	@Transactional
	public void markProductAvailability(UUID productId, boolean available) {
		if (available) {
			guards.forEach(guard -> guard.requireCanBeMarkedAvailable(productId));
		}
		setProductAvailability(productId, available);
	}

	@Transactional
	public void setProductAvailability(UUID productId, boolean available) {
		var now = Instant.now(clock);
		var flipped = products.updateAvailability(productId, available, now) == 1;
		if (!flipped && !products.existsById(productId)) {
			throw new BusinessException(CatalogError.PRODUCT_NOT_FOUND, "Product not found");
		}
		if (flipped) {
			publish(productId, AvailabilityTarget.PRODUCT, available, now,
					products.findById(productId).map(Product::getName).orElse(null));
		}
	}

	@Transactional
	public void setOptionAvailability(UUID optionId, boolean available) {
		var now = Instant.now(clock);
		var flipped = options.updateAvailability(optionId, available) == 1;
		if (!flipped && !options.existsById(optionId)) {
			throw new BusinessException(CatalogError.MODIFIER_OPTION_NOT_FOUND, "Modifier option not found");
		}
		if (flipped) {
			publish(optionId, AvailabilityTarget.MODIFIER_OPTION, available, now,
					options.findById(optionId).map(ModifierOption::getName).orElse(null));
		}
	}

	private void publish(UUID targetId, AvailabilityTarget target, boolean available, Instant now,
			@Nullable String name) {
		changes.publish(new AvailabilityChanged(targetId, target, available, currentActor.id(), currentActor.role(),
				now, name));
	}

}
