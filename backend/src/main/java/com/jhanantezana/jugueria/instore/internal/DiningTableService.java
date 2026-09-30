package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
public class DiningTableService {

	private final DiningTableRepository tables;

	private final Clock clock;

	DiningTableService(DiningTableRepository tables, Clock clock) {
		this.tables = tables;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DiningTable> list() {
		return tables.findAllByOrderByDisplayOrderAscNameAsc();
	}

	@Transactional
	public DiningTable create(String name, @Nullable String area, int displayOrder) {
		var table = new DiningTable(name, area, displayOrder, Instant.now(clock));
		try {
			tables.saveAndFlush(table);
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		return table;
	}

	@Transactional
	public DiningTable change(UUID id, String name, @Nullable String area, int displayOrder, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		table.change(name, area, displayOrder, Instant.now(clock));
		try {
			tables.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw translateName(e);
		}
		return table;
	}

	@Transactional
	public DiningTable deactivate(UUID id, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		if (table.isActive()) {
			table.deactivate(Instant.now(clock));
		}
		return table;
	}

	@Transactional
	public DiningTable reactivate(UUID id, long expectedVersion) {
		var table = findOrThrow(id);
		EntityVersions.requireMatching(table.getVersion(), expectedVersion);
		if (!table.isActive()) {
			table.reactivate(Instant.now(clock));
		}
		return table;
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
