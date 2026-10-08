package com.jhanantezana.jugueria.instore.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.TableStatus;

@Service
public class TableGridService {

	private final DiningTableRepository tables;

	TableGridService(DiningTableRepository tables) {
		this.tables = tables;
	}

	@Transactional(readOnly = true)
	public List<TableGridEntry> grid() {
		return tables.findAllByActiveTrueOrderByDisplayOrderAscNameAsc()
			.stream()
			.map(table -> new TableGridEntry(table, TableStatus.FREE, null))
			.toList();
	}

}
