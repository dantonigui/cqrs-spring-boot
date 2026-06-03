package com.project.cqrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CqrsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CqrsApplication.class, args);
	}

}
