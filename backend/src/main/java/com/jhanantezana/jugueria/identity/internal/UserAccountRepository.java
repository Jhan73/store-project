package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jhanantezana.jugueria.shared.Role;

// Public: identity.web integration tests seed accounts directly through this repository.
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByEmailIgnoreCase(String email);

	// Excludes CUSTOMER accounts: the staff-admin API never lists or exposes customer rows.
	Page<UserAccount> findByRoleNot(Role role, Pageable pageable);

	long countByRoleAndActiveTrue(Role role);

	// Atomic so concurrent failures all count; an expired lock restarts the count instead of relocking.
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
