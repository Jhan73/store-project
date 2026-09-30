package com.jhanantezana.jugueria.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Role;

// Not @Transactional: the email call after commit must run outside the DB transaction.
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
		return provisionAndNotify(staffAccounts.createAccountAndToken(email, role));
	}

	public Optional<ProvisionedStaff> createFirstAdmin(String email) {
		return staffAccounts.createFirstAdminAccountAndToken(email).map(this::provisionAndNotify);
	}

	public void resendSetPasswordLink(UUID id) {
		provisionAndNotify(staffAccounts.reissueSetPasswordToken(id));
	}

	private ProvisionedStaff provisionAndNotify(StaffProvisioned provisioned) {
		var link = SetPasswordLinks.build(setPasswordProperties, provisioned.rawSetPasswordToken());
		try {
			notifications.sendStaffSetPasswordEmail(provisioned.account().getEmail(), link,
					provisioned.setPasswordTokenExpiresAt());
		}
		catch (RuntimeException e) {
			// The account and its token exist regardless of email delivery; never log the link itself.
			log.error("Failed to send the set-password email", e);
		}
		return new ProvisionedStaff(provisioned.account(), link, provisioned.setPasswordTokenExpiresAt());
	}

}
