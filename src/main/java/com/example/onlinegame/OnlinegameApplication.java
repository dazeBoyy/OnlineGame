package com.example.onlinegame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = "com.example.onlinegame")
public class OnlinegameApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnlinegameApplication.class, args);
	}

}
