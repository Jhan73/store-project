package com.jhanantezana.jugueria.notifications.internal;

// Port: no dependency for an SMTP/SES transport is approved yet (tech-spec §3), so both adapters are transport-free.
interface EmailSender {

	void send(EmailMessage message);

}
