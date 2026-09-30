package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.catalog.AvailabilityTarget;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

// The "86": no If-Match, because setting an absolute value is idempotent and staff act on a live screen.
@Service
public class AvailabilityService {

	private final ProductRepository products;

	private final ModifierOptionRepository options;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	AvailabilityService(ProductRepository products, ModifierOptionRepository options, CatalogChanges changes,
			CurrentActor currentActor, Clock clock) {
		this.products = products;
		this.options = options;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional
	public void setProductAvailability(UUID productId, boolean available) {
		var now = Instant.now(clock);
		var flipped = products.updateAvailability(productId, available, now) == 1;
		if (!flipped && !products.existsById(productId)) {
			throw new BusinessException(CatalogError.PRODUCT_NOT_FOUND, "Product not found");
		}
		if (flipped) {
			publish(productId, AvailabilityTarget.PRODUCT, available, now);
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
			publish(optionId, AvailabilityTarget.MODIFIER_OPTION, available, now);
		}
	}

	private void publish(UUID targetId, AvailabilityTarget target, boolean available, Instant now) {
		changes.publish(new AvailabilityChanged(targetId, target, available, currentActor.id(), currentActor.role(),
				now));
	}

}
