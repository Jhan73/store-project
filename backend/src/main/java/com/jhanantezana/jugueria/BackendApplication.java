package com.jhanantezana.jugueria;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		var app = new SpringApplication(BackendApplication.class);
		if (isBootstrapOnly(args)) {
			app.setWebApplicationType(WebApplicationType.NONE);
		}
		app.run(args);
	}

	// A bootstrap run does not serve traffic, so it must not bind the port a real task already holds.
	static boolean isBootstrapOnly(String[] args) {
		ApplicationArguments arguments = new DefaultApplicationArguments(args);
		return arguments.containsOption("bootstrap-first-admin");
	}

}
