package com.jhanantezana.jugueria.notifications.internal;

// Port: no transport is wired up yet, so both adapters stay transport-free.
interface EmailSender {

	void send(EmailMessage message);

}
