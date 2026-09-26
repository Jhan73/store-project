package com.jhanantezana.jugueria.identity.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ActiveAdminLock {

	// Arbitrary fixed key; every caller must use this same one to actually serialize against each other.
	private static final long KEY = 892_614_005L;

	private final JdbcTemplate jdbcTemplate;

	ActiveAdminLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Held until the current transaction ends; a concurrent caller blocks here instead of racing the count.
	void acquire() {
		jdbcTemplate.queryForObject("select pg_advisory_xact_lock(?)", Object.class, KEY);
	}

}
