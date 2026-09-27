package com.jhanantezana.jugueria.notifications.internal;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

// Needed so the store-event listeners below run off the publisher's own thread, after its commit.
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration implements AsyncConfigurer {

	// Explicit: the STOMP broker registers several other TaskExecutor beans, making the default ambiguous.
	@Override
	@Bean
	public Executor getAsyncExecutor() {
		var executor = new SimpleAsyncTaskExecutor("app-events-signal-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
