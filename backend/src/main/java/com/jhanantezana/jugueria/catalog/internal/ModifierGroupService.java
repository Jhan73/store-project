package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.StoreApi;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class ModifierGroupService {

	private final ModifierGroupRepository groups;

	private final StoreApi store;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	private final EntityManager entityManager;

	ModifierGroupService(ModifierGroupRepository groups, StoreApi store, CatalogChanges changes,
			CurrentActor currentActor, Clock clock, EntityManager entityManager) {
		this.entityManager = entityManager;
		this.groups = groups;
		this.store = store;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ModifierGroup> list() {
		var all = groups.findAllByOrderByNameAsc();
		all.forEach(ModifierGroupService::initializeAllergens);
		return all;
	}

	@Transactional(readOnly = true)
	public ModifierGroup get(UUID id) {
		var group = groups.findWithOptionsById(id).orElseThrow(ModifierGroupService::notFound);
		initializeAllergens(group);
		return group;
	}

	// Fetching allergens in the same join as the options repeats every option once per allergen, so they load
	// separately (in batches) here, while the transaction is still open for the response mapping.
	private static void initializeAllergens(ModifierGroup group) {
		group.getOptions().forEach(option -> Hibernate.initialize(option.getAllergens()));
	}

	@Transactional
	public ModifierGroup create(String name, boolean required, int minChoices, int maxChoices,
			List<ModifierOptionDefinition> options) {
		requireStoreCurrency(options);
		var now = Instant.now(clock);
		var group = new ModifierGroup(name.strip(), required, minChoices, maxChoices, stripped(options), now);
		try {
			groups.saveAndFlush(group);
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		changes.publish(new ModifierGroupChanged(group.getId(), null, group.snapshot(), currentActor.id(),
				currentActor.role(), now));
		return group;
	}

	@Transactional
	public ModifierGroup change(UUID id, String name, boolean required, int minChoices, int maxChoices,
			List<ModifierOptionDefinition> options, long expectedVersion) {
		var group = groups.findWithOptionsById(id).orElseThrow(ModifierGroupService::notFound);
		EntityVersions.requireMatching(group.getVersion(), expectedVersion);
		requireStoreCurrency(options);
		var before = group.snapshot();
		var versionBefore = group.getVersion();
		var now = Instant.now(clock);
		group.change(name.strip(), required, minChoices, maxChoices, stripped(options), now);
		try {
			groups.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		// A change to an option's own fields leaves the group row untouched, so the bump has to be forced.
		if (group.getVersion() == versionBefore) {
			entityManager.lock(group, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		}
		changes.publish(new ModifierGroupChanged(group.getId(), before, group.snapshot(), currentActor.id(),
				currentActor.role(), now));
		return group;
	}

	// A group a product still attaches cannot go: the foreign key refuses it, and that refusal is the guard.
	@Transactional
	public void delete(UUID id, long expectedVersion) {
		var group = groups.findWithOptionsById(id).orElseThrow(ModifierGroupService::notFound);
		EntityVersions.requireMatching(group.getVersion(), expectedVersion);
		var before = group.snapshot();
		var now = Instant.now(clock);
		groups.delete(group);
		try {
			groups.flush();
		}
		catch (DataIntegrityViolationException e) {
			if (!Constraints.violated(e, Constraints.PRODUCT_MODIFIER_GROUP_FK)) {
				throw e;
			}
			throw new BusinessException(CatalogError.MODIFIER_GROUP_IN_USE,
					"The modifier group is still attached to a product");
		}
		changes.publish(new ModifierGroupChanged(id, before, null, currentActor.id(), currentActor.role(), now));
	}

	private void requireStoreCurrency(List<ModifierOptionDefinition> options) {
		var storeCurrency = store.currency();
		if (options.stream().anyMatch(option -> !option.priceDelta().currency().equals(storeCurrency))) {
			throw new BusinessException(CatalogError.CURRENCY_MISMATCH,
					"Option prices must use the store currency " + storeCurrency.getCurrencyCode());
		}
	}

	private static List<ModifierOptionDefinition> stripped(List<ModifierOptionDefinition> options) {
		return options.stream()
			.map(option -> new ModifierOptionDefinition(option.id(), option.name().strip(), option.priceDelta(),
					option.allergens()))
			.toList();
	}

	private static BusinessException notFound() {
		return new BusinessException(CatalogError.MODIFIER_GROUP_NOT_FOUND, "Modifier group not found");
	}

	private static RuntimeException translateName(DataIntegrityViolationException e) {
		return Constraints.violated(e, Constraints.MODIFIER_GROUP_NAME) ? nameAlreadyUsed() : e;
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.MODIFIER_GROUP_NAME_ALREADY_USED,
				"A modifier group with this name already exists");
	}

}
