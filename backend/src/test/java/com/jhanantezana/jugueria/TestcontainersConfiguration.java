package com.jhanantezana.jugueria;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Connecting as the container's owner would hide every missing grant until a deploy, so the
	// container reproduces the roles the bootstrap runbook creates and the app connects as app.
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
		.withDatabaseName("jugueria")
		.withUsername("jugueria_admin")
		.withPassword("admin")
		.withInitScript("db/least-privilege-roles.sql");

	static {
		POSTGRES.start();
	}

	@Bean
	JdbcConnectionDetails jdbcConnectionDetails() {
		return new JdbcConnectionDetails() {

			@Override
			public String getJdbcUrl() {
				return POSTGRES.getJdbcUrl();
			}

			@Override
			public String getUsername() {
				return "app";
			}

			@Override
			public String getPassword() {
				return "app";
			}

		};
	}

}
