package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TableCreated;
import com.jhanantezana.jugueria.instore.TableSnapshot;
import com.jhanantezana.jugueria.instore.TableStatusChanged;
import com.jhanantezana.jugueria.instore.TableUpdated;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

@Service
public class DiningTableService {

	private final DiningTableRepository tables;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	DiningTableService(DiningTableRepository tables, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.tables = tables;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DiningTable> list() {
		return tables.findAllByOrderByDisplayOrderAscNameAsc();
	}

	@Transactional
	public DiningTable create(String name, @Nullable String area, int displayOrder) {
		var now = Instant.now(clock);
		var table = new DiningTable(name, area, displayOrder, now);
		try {
			tables.saveAndFlush(table);
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		events.publishEvent(
				new TableCreated(table.getId(), snapshot(table), currentActor.id(), currentActor.role(), now));
		return table;
	}

	@Transactional
	public DiningTable change(UUID id, String name, @Nullable String area, int displayOrder, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		var before = snapshot(table);
		var now = Instant.now(clock);
		table.change(name, area, displayOrder, now);
		try {
			tables.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		events.publishEvent(new TableUpdated(table.getId(), before, snapshot(table), currentActor.id(),
				currentActor.role(), now));
		return table;
	}

	@Transactional
	public DiningTable deactivate(UUID id, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		if (table.isActive()) {
			var now = Instant.now(clock);
			table.deactivate(now);
			publishStatus(table, now);
		}
		return table;
	}

	@Transactional
	public DiningTable reactivate(UUID id, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		if (!table.isActive()) {
			var now = Instant.now(clock);
			table.reactivate(now);
			publishStatus(table, now);
		}
		return table;
	}

	private void publishStatus(DiningTable table, Instant now) {
		events.publishEvent(
				new TableStatusChanged(table.getId(), table.isActive(), currentActor.id(), currentActor.role(), now));
	}

	private static TableSnapshot snapshot(DiningTable table) {
		return new TableSnapshot(table.getName(), table.getArea(), table.getDisplayOrder());
	}

	private DiningTable findOrThrow(UUID id) {
		return tables.findById(id)
			.orElseThrow(() -> new BusinessException(InstoreError.TABLE_NOT_FOUND, "Table not found"));
	}

	private static RuntimeException translateName(DataIntegrityViolationException e) {
		if (Constraints.violated(e, Constraints.TABLE_NAME)) {
			return new BusinessException(InstoreError.TABLE_NAME_ALREADY_USED, "A table with this name already exists");
		}
		return e;
	}

}
