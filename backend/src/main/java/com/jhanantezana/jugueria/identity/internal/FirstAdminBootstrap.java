package com.jhanantezana.jugueria.identity.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

// Idempotent: safe to rerun. Only ever prints to stdout of this one-off run, never through the app logger.
@Component
class FirstAdminBootstrap {

	private final StaffProvisioningService provisioning;

	FirstAdminBootstrap(StaffProvisioningService provisioning) {
		this.provisioning = provisioning;
	}

	int run(@Nullable String adminEmail) {
		if (adminEmail == null || adminEmail.isBlank()) {
			System.err.println("Missing required --admin-email=<email> argument.");
			return 1;
		}
		var provisioned = provisioning.createFirstAdmin(adminEmail);
		if (provisioned.isEmpty()) {
			System.out.println("An active ADMIN already exists; nothing to do.");
			return 0;
		}
		System.out.println("First ADMIN created: " + provisioned.get().account().getEmail());
		System.out.println("Set-password link (expires " + provisioned.get().linkExpiresAt() + "): "
				+ provisioned.get().setPasswordLink());
		return 0;
	}

}
