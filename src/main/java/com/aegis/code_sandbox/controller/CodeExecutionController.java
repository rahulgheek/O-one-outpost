package com.aegis.code_sandbox.controller;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.dto.ExecutionRequest;
import com.aegis.code_sandbox.dto.ExecutionResult;
import com.aegis.code_sandbox.judge.Language;
import com.aegis.code_sandbox.service.CodeExecutionService;
import com.aegis.code_sandbox.service.ContainerPool;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/sandbox")
public class CodeExecutionController {

    private final CodeExecutionService executionService;
    private final ContainerPool containerPool;
    private final JudgeProperties properties;

    CodeExecutionController(
            CodeExecutionService codeExecutionService,
            ContainerPool containerPool,
            JudgeProperties properties
    ) {
        this.executionService = codeExecutionService;
        this.containerPool = containerPool;
        this.properties = properties;
    }

    /**
     * Legacy plain-text contract used by arena-service and assessment-service.
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> executeCode(@RequestBody ExecutionRequest request) {
        validate(request);
        ExecutionResult result = executionService.executeCode(request);
        return ResponseEntity.ok()
                .headers(resultHeaders(result))
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.plainTextBody());
    }

    /**
     * Structured result for dashboards, audits and debugging. The plain-text
     * endpoint above remains the production contract.
     */
    @PostMapping(value = "/execute/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExecutionResult> executeCodeDetailed(@RequestBody ExecutionRequest request) {
        validate(request);
        ExecutionResult result = executionService.executeCode(request);
        return ResponseEntity.ok()
                .headers(resultHeaders(result))
                .body(result);
    }

    /**
     * Operator-facing status endpoint. Protected by the same internal token as
     * the execution endpoints.
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SandboxStatus> status() {
        ContainerPool.PoolStatus pool = containerPool.status();
        JudgeProperties.OnlineCompiler online = properties.getOnlineCompiler();
        List<SandboxLanguage> languages = Arrays.stream(Language.values())
                .map(language -> new SandboxLanguage(
                        language.id(),
                        language.displayName(),
                        online.compilerFor(language),
                        language.editorLanguage(),
                        language.dockerSupported()))
                .toList();
        SandboxStatus status = new SandboxStatus(
                properties.isExecutionEnabled(),
                properties.normalizedBackend(),
                properties.isDockerEnabled(),
                properties.isApiOnlyMode(),
                properties.isRequireDocker(),
                properties.getImage(),
                properties.normalizedImagePullPolicy(),
                properties.getExecution().getDefaultTimeLimitMs(),
                properties.getExecution().getMaxTimeLimitMs(),
                properties.getExecution().getDefaultMemoryLimitMb(),
                properties.getExecution().getMaxMemoryLimitMb(),
                properties.getExecution().getTimeLimitMultiplier(),
                pool,
                new OnlineCompilerStatus(
                        properties.isOnlineCompilerConfigured(),
                        online.getBaseUrl(),
                        online.getCompiler(),
                        online.getMaxConcurrency(),
                        online.getMaxCodeBytes(),
                        online.getResponseTimeoutMs(),
                        online.getMemoryHeadroomMb(),
                        online.getMaxMemoryMb()),
                languages);
        return ResponseEntity.ok(status);
    }

    private void validate(ExecutionRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        // Rejects unknown ids with HTTP 400 before any provider quota is used.
        Language.resolve(request.language());
        int codeBytes = request.code().getBytes(StandardCharsets.UTF_8).length;
        if (codeBytes > properties.effectiveMaxCodeBytes()) {
            throw new IllegalArgumentException("code exceeds the "
                    + properties.effectiveMaxCodeBytes() + "-byte limit");
        }
    }

    private HttpHeaders resultHeaders(ExecutionResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Judge-Verdict", result.verdict().name());
        headers.set("X-Execution-Time-Ms", Long.toString(result.timeMs()));
        headers.set("X-Wall-Time-Ms", Long.toString(result.wallTimeMs()));
        headers.set("X-Memory-Used-Kb", Long.toString(result.memoryUsedKb()));
        headers.set("X-Execution-Exit-Code", Integer.toString(result.exitCode()));
        headers.set("X-Output-Truncated", Boolean.toString(result.outputTruncated()));
        return headers;
    }

    public record SandboxStatus(
            boolean executionEnabled,
            String backend,
            boolean dockerBackendActive,
            boolean apiOnlyMode,
            boolean requireDocker,
            String image,
            String imagePullPolicy,
            int defaultTimeLimitMs,
            int maxTimeLimitMs,
            int defaultMemoryLimitMb,
            int maxMemoryLimitMb,
            double timeLimitMultiplier,
            ContainerPool.PoolStatus pool,
            OnlineCompilerStatus onlineCompiler,
            List<SandboxLanguage> languages
    ) {
    }

    public record SandboxLanguage(
            String id,
            String displayName,
            String compiler,
            String editorLanguage,
            boolean dockerSupported
    ) {
    }

    public record OnlineCompilerStatus(
            boolean configured,
            String baseUrl,
            String compiler,
            int maxConcurrency,
            int maxCodeBytes,
            long responseTimeoutMs,
            int memoryHeadroomMb,
            int maxMemoryMb
    ) {
    }
}
