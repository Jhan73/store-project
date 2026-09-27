package com.jhanantezana.jugueria.store.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, UUID> {
}
