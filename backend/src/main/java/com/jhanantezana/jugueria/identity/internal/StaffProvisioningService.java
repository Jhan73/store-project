package com.jhanantezana.jugueria.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Role;

// Orchestrates the DB transaction and the post-commit email call: never call an external system inside a
// transaction (backend/CLAUDE.md), so this class itself is deliberately not @Transactional.
@Service
public class StaffProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(StaffProvisioningService.class);

	private final StaffAccountService staffAccounts;

	private final NotificationsApi notifications;

	private final IdentityProperties.SetPassword setPasswordProperties;

	StaffProvisioningService(StaffAccountService staffAccounts, NotificationsApi notifications,
			IdentityProperties identityProperties) {
		this.staffAccounts = staffAccounts;
		this.notifications = notifications;
		this.setPasswordProperties = identityProperties.setPassword();
	}

	public ProvisionedStaff createStaff(String email, Role role) {
		var provisioned = staffAccounts.createAccountAndToken(email, role);
		var link = SetPasswordLinks.build(setPasswordProperties, provisioned.rawSetPasswordToken());
		try {
			notifications.sendStaffSetPasswordEmail(provisioned.account().getEmail(), link,
					provisioned.setPasswordTokenExpiresAt());
		}
		catch (RuntimeException e) {
			// The account and its token exist regardless of email delivery; never log the link itself.
			log.error("Failed to send the set-password email for a new staff account", e);
		}
		return new ProvisionedStaff(provisioned.account(), link, provisioned.setPasswordTokenExpiresAt());
	}

}
