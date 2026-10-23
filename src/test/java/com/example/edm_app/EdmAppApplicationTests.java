package com.example.edm_app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:edmtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"jwt.secret=unit-test-jwt-secret-with-over-32-characters",
		"app.ml.token=unit-test-ml-service-token-with-over-32-characters"
})
class EdmAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
