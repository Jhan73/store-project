package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

// EnableCaching switches on Boot's cache auto-configuration, which builds the Caffeine cache the menu lives in.
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogConfiguration {

}
