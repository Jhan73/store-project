package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.catalog.CategorySnapshot;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class CategoryService {

	private final CategoryRepository categories;

	private final StationRepository stations;

	private final ProductRepository products;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	CategoryService(CategoryRepository categories, StationRepository stations, ProductRepository products,
			CatalogChanges changes, CurrentActor currentActor, Clock clock) {
		this.categories = categories;
		this.stations = stations;
		this.products = products;
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
		publish(category, CatalogChangeKind.CREATED, null, now);
		return category;
	}

	@Transactional
	public Category change(UUID id, String name, UUID stationId, int displayOrder, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		var now = Instant.now(clock);
		var before = category.snapshot();
		category.change(name.strip(), stationId.equals(category.getStationId()) ? stationId : resolveStation(stationId),
				displayOrder, now);
		try {
			categories.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		publish(category, CatalogChangeKind.UPDATED, before, now);
		return category;
	}

	/** The given categories no product belongs to, which are the only ones that can be deleted. */
	@Transactional(readOnly = true)
	public Set<UUID> deletable(Collection<UUID> categoryIds) {
		if (categoryIds.isEmpty()) {
			return Set.of();
		}
		var deletable = new HashSet<>(categoryIds);
		deletable.removeAll(products.categoryIdsWithProducts(categoryIds));
		return deletable;
	}

	// The foreign key from product is the guard: a product created meanwhile refuses the delete.
	@Transactional
	public void delete(UUID id, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		var before = category.snapshot();
		var now = Instant.now(clock);
		categories.delete(category);
		try {
			categories.flush();
		}
		catch (DataIntegrityViolationException e) {
			if (!Constraints.violated(e, Constraints.PRODUCT_CATEGORY_FK)) {
				throw e;
			}
			throw categoryInUse();
		}
		changes.publish(new CategoryChanged(id, CatalogChangeKind.DELETED, before, null, currentActor.id(),
				currentActor.role(), now));
	}

	@Transactional
	public Category deactivate(UUID id, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		if (!category.isActive()) {
			return category;
		}
		var now = Instant.now(clock);
		var before = category.snapshot();
		category.deactivate(now);
		publish(category, CatalogChangeKind.DEACTIVATED, before, now);
		return category;
	}

	@Transactional
	public Category reactivate(UUID id, long expectedVersion) {
		var category = findOrThrow(id);
		EntityVersions.requireMatching(category.getVersion(), expectedVersion);
		if (category.isActive()) {
			return category;
		}
		requireActiveStation(category.getStationId());
		var now = Instant.now(clock);
		var before = category.snapshot();
		category.reactivate(now);
		publish(category, CatalogChangeKind.ACTIVATED, before, now);
		return category;
	}

	private UUID resolveStation(@Nullable UUID stationId) {
		if (stationId == null) {
			return stations.findByDefaultStationTrue()
				.orElseThrow(() -> new IllegalStateException("Expected the seeded default station"))
				.getId();
		}
		var station = stations.findForShareById(stationId)
			.orElseThrow(() -> new BusinessException(CatalogError.UNKNOWN_STATION,
					"Station " + stationId + " does not exist"));
		requireActive(station);
		return stationId;
	}

	// The share lock makes this check and the station's own deactivation a single ordered decision.
	private void requireActiveStation(UUID stationId) {
		stations.findForShareById(stationId).ifPresent(CategoryService::requireActive);
	}

	private static void requireActive(Station station) {
		if (!station.isActive()) {
			throw new BusinessException(CatalogError.STATION_INACTIVE,
					"Station " + station.getId() + " is inactive; reactivate it first");
		}
	}

	private void publish(Category category, CatalogChangeKind kind, @Nullable CategorySnapshot before, Instant now) {
		changes.publish(new CategoryChanged(category.getId(), kind, before, category.snapshot(), currentActor.id(),
				currentActor.role(), now));
	}

	private Category findOrThrow(UUID id) {
		return categories.findById(id)
			.orElseThrow(() -> new BusinessException(CatalogError.CATEGORY_NOT_FOUND, "Category not found"));
	}

	private static BusinessException categoryInUse() {
		return new BusinessException(CatalogError.CATEGORY_IN_USE,
				"The category still has products; deactivate it instead");
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.CATEGORY_NAME_ALREADY_USED,
				"A category with this name already exists");
	}

}
