package com.aegis.gateway_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.boot.SpringApplication.*;

@SpringBootApplication
@EnableScheduling
public class GatewayServiceApplication {

	public static void main(String[] args) {
		run(GatewayServiceApplication.class, args);
	}

}
