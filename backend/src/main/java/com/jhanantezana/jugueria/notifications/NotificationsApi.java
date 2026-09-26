package com.jhanantezana.jugueria.notifications;

import java.net.URI;
import java.time.Instant;

public interface NotificationsApi {

	void sendStaffSetPasswordEmail(String toEmail, URI setPasswordLink, Instant linkExpiresAt);

}
