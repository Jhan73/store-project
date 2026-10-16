package com.jhanantezana.jugueria.inventory.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryProperties.class)
class InventoryConfiguration {

}
