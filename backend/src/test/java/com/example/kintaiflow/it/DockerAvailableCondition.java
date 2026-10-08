package com.example.kintaiflow.it;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * 結合テストは Docker が必要。手元で Docker が動いていなければ、失敗ではなく「スキップ」にする。
 * ただし CI（環境変数 CI が設定されている）では、Docker が無いまま緑になるのを防ぐため、スキップせずに失敗させる。
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        boolean available = DockerClientFactory.instance().isDockerAvailable();
        if (available) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        if (System.getenv("CI") != null) {
            throw new IllegalStateException("Docker is required for integration tests on CI, but it is not available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available: integration tests are skipped");
    }
}
