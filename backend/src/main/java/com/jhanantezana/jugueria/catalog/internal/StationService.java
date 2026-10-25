package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.StationChanged;
import com.jhanantezana.jugueria.catalog.StationDeactivationGuard;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class StationService {

	private final StationRepository stations;

	private final CategoryRepository categories;

	private final List<StationDeactivationGuard> deactivationGuards;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	StationService(StationRepository stations, CategoryRepository categories,
			List<StationDeactivationGuard> deactivationGuards, CatalogChanges changes, CurrentActor currentActor,
			Clock clock) {
		this.stations = stations;
		this.categories = categories;
		this.deactivationGuards = deactivationGuards;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Station> list() {
		return stations.findAllByOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	public List<Station> listActive() {
		return stations.findAllByActiveTrueOrderByNameAsc();
	}

	// Retiring a station is refused while anything still routes to it: an active category (new lines would land
	// there) or a live card on the board (the kitchen would lose it from its station view).
	@Transactional
	public Station deactivate(UUID id, long expectedVersion) {
		var station = stations.findForUpdateById(id).orElseThrow(StationService::notFound);
		EntityVersions.requireMatching(station.getVersion(), expectedVersion);
		if (!station.isActive()) {
			return station;
		}
		if (station.isDefaultStation()) {
			throw new BusinessException(CatalogError.DEFAULT_STATION_REQUIRED,
					"The default station is where categories land when they name none, so it stays active");
		}
		if (categories.existsByStationIdAndActiveTrue(id)) {
			throw new BusinessException(CatalogError.STATION_HAS_ACTIVE_CATEGORIES,
					"An active category still uses the station");
		}
		deactivationGuards.forEach(guard -> guard.requireCanDeactivate(id));
		var now = Instant.now(clock);
		var before = station.snapshot();
		station.deactivate(now);
		stations.flush();
		changes.publish(new StationChanged(id, CatalogChangeKind.DEACTIVATED, before, station.snapshot(),
				currentActor.id(), currentActor.role(), now));
		return station;
	}

	@Transactional
	public Station reactivate(UUID id, long expectedVersion) {
		var station = stations.findById(id).orElseThrow(StationService::notFound);
		EntityVersions.requireMatching(station.getVersion(), expectedVersion);
		if (station.isActive()) {
			return station;
		}
		var now = Instant.now(clock);
		var before = station.snapshot();
		station.reactivate(now);
		stations.flush();
		changes.publish(new StationChanged(id, CatalogChangeKind.ACTIVATED, before, station.snapshot(),
				currentActor.id(), currentActor.role(), now));
		return station;
	}

	@Transactional
	public Station create(String name) {
		var now = Instant.now(clock);
		var station = new Station(name.strip(), now);
		try {
			stations.saveAndFlush(station);
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		changes.publish(new StationChanged(station.getId(), CatalogChangeKind.CREATED, null, station.snapshot(),
				currentActor.id(), currentActor.role(), now));
		return station;
	}

	@Transactional
	public Station rename(UUID id, String name, long expectedVersion) {
		var station = stations.findById(id).orElseThrow(StationService::notFound);
		EntityVersions.requireMatching(station.getVersion(), expectedVersion);
		var now = Instant.now(clock);
		var before = station.snapshot();
		station.rename(name.strip(), now);
		try {
			stations.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		changes.publish(new StationChanged(station.getId(), CatalogChangeKind.UPDATED, before, station.snapshot(),
				currentActor.id(), currentActor.role(), now));
		return station;
	}

	private static BusinessException notFound() {
		return new BusinessException(CatalogError.STATION_NOT_FOUND, "Station not found");
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.STATION_NAME_ALREADY_USED, "A station with this name already exists");
	}

}
