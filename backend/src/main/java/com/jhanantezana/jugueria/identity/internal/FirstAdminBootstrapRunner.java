package com.jhanantezana.jugueria.identity.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

// Run as a one-off, e.g. an ECS run-task overriding the container command with
// "--bootstrap-first-admin --admin-email=<email>" (docs/runbooks/first-admin-bootstrap.md).
// A no-op on every normal start, so this bean is always registered but never fires in the running service.
@Component
class FirstAdminBootstrapRunner implements ApplicationRunner {

	static final String OPTION = "bootstrap-first-admin";

	static final String EMAIL_OPTION = "admin-email";

	private final FirstAdminBootstrap bootstrap;

	private final ConfigurableApplicationContext context;

	FirstAdminBootstrapRunner(FirstAdminBootstrap bootstrap, ConfigurableApplicationContext context) {
		this.bootstrap = bootstrap;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.containsOption(OPTION)) {
			return;
		}
		var emailValues = args.getOptionValues(EMAIL_OPTION);
		var email = emailValues == null || emailValues.isEmpty() ? null : emailValues.getFirst();
		var exitCode = bootstrap.run(email);
		System.exit(SpringApplication.exit(context, () -> exitCode));
	}

}
