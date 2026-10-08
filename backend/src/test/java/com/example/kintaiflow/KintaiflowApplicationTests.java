package com.example.kintaiflow;

import com.example.kintaiflow.it.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 起動の煙テスト：Spring コンテキストが立ち上がり、空の PostgreSQL に Flyway の全マイグレーション（V1〜）が流れ、
 * JPA のスキーマ検証（ddl-auto=validate）を通ることを確認する。
 */
class KintaiflowApplicationTests extends IntegrationTestBase {

	@Test
	void contextLoadsAndSchemaIsMigratedFromScratch() {
		// 年次有給休暇は Flyway の初期データで入っている
		assertTrue(leaveTypeRepository.findByName("年次有給休暇").isPresent());
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}
}
