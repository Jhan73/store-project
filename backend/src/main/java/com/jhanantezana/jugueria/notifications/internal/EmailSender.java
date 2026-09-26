package com.jhanantezana.jugueria.notifications.internal;

// Public within internal/ so the smtp and ses sub-packages can implement it.
public interface EmailSender {

	void send(EmailMessage message);

}
