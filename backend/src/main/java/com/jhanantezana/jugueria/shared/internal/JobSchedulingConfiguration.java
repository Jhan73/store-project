package com.jhanantezana.jugueria.shared.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// Every @Scheduled method and SchedulingConfigurer task runs here, never on the STOMP heartbeat scheduler.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class JobSchedulingConfiguration implements SchedulingConfigurer {

	private final ThreadPoolTaskScheduler jobTaskScheduler;

	JobSchedulingConfiguration(@Qualifier("jobTaskScheduler") ThreadPoolTaskScheduler jobTaskScheduler) {
		this.jobTaskScheduler = jobTaskScheduler;
	}

	@Bean(destroyMethod = "shutdown")
	static ThreadPoolTaskScheduler jobTaskScheduler() {
		var scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("job-");
		return scheduler;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.setTaskScheduler(jobTaskScheduler);
	}

}
