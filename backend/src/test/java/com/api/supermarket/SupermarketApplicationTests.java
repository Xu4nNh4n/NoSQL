package com.api.supermarket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class SupermarketApplicationTests {

	@Test
	void contextLoads() {
	}

}
