package com.jhanantezana.jugueria.shared;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Test-only entity: its table comes from a migration under src/test/resources, never packaged.
@Entity
@Table(schema = "test_probe", name = "probe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProbeEntity extends BaseEntity {

	private Money price;

	ProbeEntity(Money price) {
		this.price = price;
	}

}
