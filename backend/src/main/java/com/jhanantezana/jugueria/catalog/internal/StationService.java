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
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class StationService {

	private final StationRepository stations;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	StationService(StationRepository stations, CatalogChanges changes, CurrentActor currentActor, Clock clock) {
		this.stations = stations;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Station> list() {
		return stations.findAllByOrderByNameAsc();
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
		changes.publish(new StationChanged(station.getId(), CatalogChangeKind.CREATED, currentActor.id(),
				currentActor.role(), now));
		return station;
	}

	@Transactional
	public Station rename(UUID id, String name, long expectedVersion) {
		var station = stations.findById(id).orElseThrow(StationService::notFound);
		EntityVersions.requireMatching(station.getVersion(), expectedVersion);
		var now = Instant.now(clock);
		station.rename(name.strip(), now);
		try {
			stations.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		changes.publish(new StationChanged(station.getId(), CatalogChangeKind.UPDATED, currentActor.id(),
				currentActor.role(), now));
		return station;
	}

	private static BusinessException notFound() {
		return new BusinessException(CatalogError.STATION_NOT_FOUND, "Station not found");
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.STATION_NAME_ALREADY_USED, "A station with this name already exists");
	}

}
