package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WalletProviderRepository extends JpaRepository<WalletProvider, UUID> {

	List<WalletProvider> findAllByOrderByNameAsc();

	List<WalletProvider> findAllByActiveTrueOrderByNameAsc();

	// A payment holds this share lock until it commits, so a deactivation waits for it instead of slipping between
	// the active check and the payment row.
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select p from WalletProvider p where p.id = :id")
	Optional<WalletProvider> findForShareById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from WalletProvider p where p.id = :id")
	Optional<WalletProvider> findForUpdateById(@Param("id") UUID id);

}
