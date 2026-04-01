package com.slavacom.vkmidsprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VkMidSprintApplication {

	public static void main(String[] args) {
		SpringApplication.run(VkMidSprintApplication.class, args);
	}

}
