package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportingProperties.class)
class ReportingConfiguration {

}
