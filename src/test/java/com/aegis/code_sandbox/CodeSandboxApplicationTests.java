package com.aegis.code_sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the application without Docker. API-only mode is explicit so the
 * context test never depends on a local or remote daemon.
 */
@SpringBootTest(properties = {
		"judge.api-only-mode=true",
		"judge.require-docker=false",
		"judge.pool-size=0",
		"judge.pool.warmup-on-startup=false"
})
class CodeSandboxApplicationTests {

	@Test
	void contextLoads() {
	}

}
