package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Public: identity.web integration tests seed accounts directly through this repository.
// No @Transactional here: it belongs only on application service methods (LoginService). Callers
// outside a service (repository ITs) must supply their own transaction, e.g. TransactionTemplate.
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByEmailIgnoreCase(String email);

	// Atomic: two concurrent failures on the same account must both increment the counter, which
	// @Version optimistic locking would instead reject one of as a concurrent-modification conflict.
	// If the previous lock already expired (locked_until <= now), the count restarts at 1 instead of
	// accumulating on top of the stale value — otherwise a single failure right after expiry would
	// immediately relock the account for a full lockout window. Native SQL (not JPQL): the nested CASE
	// mixed with a bare NULL branch left Postgres unable to infer locked_until's type from a JPQL
	// literal null, so the ELSE branch casts it explicitly.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			update identity.user_account
			set failed_attempts = case when locked_until is not null and locked_until <= :now then 1
			                            else failed_attempts + 1 end,
			    updated_at = :now,
			    locked_until = case
			        when (case when locked_until is not null and locked_until <= :now then 1
			                   else failed_attempts + 1 end) >= :maxAttempts then :lockUntil
			        else null::timestamptz
			    end
			where id = :id
			""")
	int registerFailedAttempt(@Param("id") UUID id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
			@Param("lockUntil") Instant lockUntil);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true,
			value = "update identity.user_account set failed_attempts = 0, locked_until = null, updated_at = :now where id = :id")
	int resetFailedAttempts(@Param("id") UUID id, @Param("now") Instant now);

}
