package dev.abu.screener_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScreenerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScreenerBackendApplication.class, args);
	}

}