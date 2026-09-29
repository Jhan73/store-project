package com.jhanantezana.jugueria.notifications.internal;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

// @ApplicationModuleListener enables async processing itself (spring-modulith-starter-jdbc); this only
// resolves which executor it uses, since STOMP's broker config registers several other TaskExecutor
// beans, making the unqualified default ambiguous.
@Configuration(proxyBeanMethods = false)
class AsyncConfiguration implements AsyncConfigurer {

	@Override
	@Bean
	public Executor getAsyncExecutor() {
		var executor = new SimpleAsyncTaskExecutor("app-events-signal-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
