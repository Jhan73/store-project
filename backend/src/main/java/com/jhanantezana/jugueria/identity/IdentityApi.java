package com.jhanantezana.jugueria.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

	/** Emails of the administrators who can sign in, for notices addressed to the whole admin team. */
	List<String> activeAdminEmails();

	Optional<String> emailOf(UUID userId);

	/** The name of use of a staff account; empty when it has none yet or does not exist. */
	Optional<String> usernameOf(UUID userId);

}
