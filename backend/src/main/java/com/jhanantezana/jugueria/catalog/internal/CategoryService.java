package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class CategoryService {

	private final CategoryRepository categories;

	private final StationRepository stations;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	CategoryService(CategoryRepository categories, StationRepository stations, CatalogChanges changes,
			CurrentActor currentActor, Clock clock) {
		this.categories = categories;
		this.stations = stations;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Category> list() {
		return categories.findAllByOrderByDisplayOrderAscNameAsc();
	}

	// A null station means the default one.
	@Transactional
	public Category create(String name, @Nullable UUID stationId, int displayOrder) {
		var now = Instant.now(clock);
		var category = new Category(name.strip(), resolveStation(stationId), displayOrder, now);
		try {
			categories.saveAndFlush(category);
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		publish(category, CatalogChangeKind.CREATED, now);
		return category;
	}

	@Transactional
	public Category change(UUID id, String name, UUID stationId, int displayOrder, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		var now = Instant.now(clock);
		category.change(name.strip(), resolveStation(stationId), displayOrder, now);
		try {
			categories.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		publish(category, CatalogChangeKind.UPDATED, now);
		return category;
	}

	@Transactional
	public Category deactivate(UUID id, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		if (!category.isActive()) {
			return category;
		}
		var now = Instant.now(clock);
		category.deactivate(now);
		publish(category, CatalogChangeKind.DEACTIVATED, now);
		return category;
	}

	@Transactional
	public Category reactivate(UUID id, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		if (category.isActive()) {
			return category;
		}
		var now = Instant.now(clock);
		category.reactivate(now);
		publish(category, CatalogChangeKind.ACTIVATED, now);
		return category;
	}

	private UUID resolveStation(@Nullable UUID stationId) {
		if (stationId == null) {
			return stations.findByDefaultStationTrue()
				.orElseThrow(() -> new IllegalStateException("Expected the seeded default station"))
				.getId();
		}
		if (!stations.existsById(stationId)) {
			throw new BusinessException(CatalogError.UNKNOWN_STATION, "Station " + stationId + " does not exist");
		}
		return stationId;
	}

	private void publish(Category category, CatalogChangeKind kind, Instant now) {
		changes.publish(new CategoryChanged(category.getId(), kind, currentActor.id(), currentActor.role(), now));
	}

	private Category findOrThrow(UUID id) {
		return categories.findById(id)
			.orElseThrow(() -> new BusinessException(CatalogError.CATEGORY_NOT_FOUND, "Category not found"));
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.CATEGORY_NAME_ALREADY_USED,
				"A category with this name already exists");
	}

}
