package com.example.kintaiflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "kintaiflow.batch.scheduling-enabled=false")   // テスト中にバッチを意図せず起動しない
class KintaiflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
