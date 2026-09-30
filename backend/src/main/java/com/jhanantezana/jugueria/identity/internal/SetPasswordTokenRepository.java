package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Public: identity.web integration tests seed set-password tokens directly through this repository.
public interface SetPasswordTokenRepository extends JpaRepository<SetPasswordToken, UUID> {

	Optional<SetPasswordToken> findByTokenHash(String tokenHash);

	// Atomic: the row count is the only safe way to decide whether this attempt gets to consume the token.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			update identity.set_password_token
			set used_at = :now
			where id = :id and used_at is null and revoked_at is null and expires_at > :now
			""")
	int markUsed(@Param("id") UUID id, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			update identity.set_password_token
			set revoked_at = :now
			where user_id = :userId and used_at is null and revoked_at is null
			""")
	int revokeAllUnusedForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
