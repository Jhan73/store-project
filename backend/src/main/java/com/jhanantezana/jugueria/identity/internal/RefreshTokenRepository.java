package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Public: identity.web integration tests seed and inspect refresh tokens directly through this repository.
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// Atomic: the row count is the only safe way to detect a concurrent reuse of the same token.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			update identity.refresh_token
			set used_at = :now
			where id = :id and used_at is null and revoked_at is null and expires_at > :now
			""")
	int markUsed(@Param("id") UUID id, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true,
			value = "update identity.refresh_token set revoked_at = :now where family_id = :familyId and revoked_at is null")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true,
			value = "update identity.refresh_token set revoked_at = :now where user_id = :userId and revoked_at is null")
	int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
