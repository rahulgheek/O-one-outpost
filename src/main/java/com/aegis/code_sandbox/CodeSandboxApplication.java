package com.aegis.code_sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeSandboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeSandboxApplication.class, args);
	}

}
