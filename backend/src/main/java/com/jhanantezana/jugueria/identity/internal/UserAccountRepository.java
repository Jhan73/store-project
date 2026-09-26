package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

// Public: identity.web integration tests seed accounts directly through this repository.
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByEmailIgnoreCase(String email);

	// Atomic: two concurrent failures on the same account must both increment the counter, which
	// @Version optimistic locking would instead reject one of as a concurrent-modification conflict.
	// @Transactional here (not just on the caller) so this stays atomic even called standalone.
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update UserAccount u
			set u.failedAttempts = u.failedAttempts + 1,
			    u.lockedUntil = case when u.failedAttempts + 1 >= :maxAttempts then :lockedUntil else u.lockedUntil end
			where u.id = :id
			""")
	int registerFailedAttempt(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
			@Param("lockedUntil") Instant lockedUntil);

	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update UserAccount u set u.failedAttempts = 0, u.lockedUntil = null where u.id = :id")
	int resetFailedAttempts(@Param("id") UUID id);

}
