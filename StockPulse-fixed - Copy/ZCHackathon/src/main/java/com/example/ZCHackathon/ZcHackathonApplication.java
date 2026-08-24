package com.example.ZCHackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ZcHackathonApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZcHackathonApplication.class, args);
	}

}
