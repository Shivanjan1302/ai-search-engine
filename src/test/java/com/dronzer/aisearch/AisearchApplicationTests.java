package com.dronzer.aisearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class AisearchApplicationTests {

	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> UUID.randomUUID().toString()
				+ UUID.randomUUID().toString());
	}

	@Test
	void contextLoads() {
	}

}
