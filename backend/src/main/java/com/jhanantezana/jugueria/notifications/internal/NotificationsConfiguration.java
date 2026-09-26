package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationsProperties.class)
class NotificationsConfiguration {

}
