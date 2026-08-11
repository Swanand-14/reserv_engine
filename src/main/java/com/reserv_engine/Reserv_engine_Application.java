package com.reserv_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Reserv_engine_Application {

	public static void main(String[] args) {
		SpringApplication.run(Reserv_engine_Application.class, args);
	}

}
