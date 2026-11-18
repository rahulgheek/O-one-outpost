package com.aegis.code_sandbox.judge;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Languages the judge accepts.
 *
 * <p>Every entry maps a stable, user-facing id to the OnlineCompiler.io
 * compiler id documented at {@code https://onlinecompiler.io/docs/compilers}.
 * The registry is intentionally closed: an unknown id is rejected before a
 * request reaches the provider, so a typo can never be billed or silently
 * treated as Java.</p>
 *
 * <p>Only Java is available on the Docker backend. Its executor image is a
 * JRE/JDK image; the other languages are online-only until a multi-runtime
 * executor image is introduced.</p>
 */
public enum Language {

    JAVA("java", "Java", "openjdk-25", "java", true),
    PYTHON("python", "Python", "python-3.14", "python", false),
    CPP("cpp", "C++", "g++-15", "cpp", false),
    C("c", "C", "gcc-15", "c", false),
    JAVASCRIPT("javascript", "JavaScript", "nodejs-24", "javascript", false),
    TYPESCRIPT("typescript", "TypeScript", "typescript-deno", "typescript", false),
    GO("go", "Go", "go-1.26", "go", false),
    RUST("rust", "Rust", "rust-1.93", "rust", false);

    public static final Language DEFAULT = JAVA;

    private final String id;
    private final String displayName;
    private final String defaultCompiler;
    private final String editorLanguage;
    private final boolean dockerSupported;

    Language(String id, String displayName, String defaultCompiler, String editorLanguage,
             boolean dockerSupported) {
        this.id = id;
        this.displayName = displayName;
        this.defaultCompiler = defaultCompiler;
        this.editorLanguage = editorLanguage;
        this.dockerSupported = dockerSupported;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultCompiler() {
        return defaultCompiler;
    }

    /** Monaco editor language id used by the web client. */
    public String editorLanguage() {
        return editorLanguage;
    }

    public boolean dockerSupported() {
        return dockerSupported;
    }

    /**
     * Resolves a request value. A missing value means Java for backwards
     * compatibility with the original Java-only contract.
     */
    public static Language resolve(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return find(value).orElseThrow(() -> new IllegalArgumentException(
                "Unsupported language '" + value + "'. Supported languages: " + supportedIds() + "."));
    }

    public static Optional<Language> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(DEFAULT);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.id.equals(normalized)) {
                return Optional.of(language);
            }
        }
        return switch (normalized) {
            case "py", "python3", "python-3", "python3.14" -> Optional.of(PYTHON);
            case "c++", "cplusplus", "cpp17", "cpp20", "g++" -> Optional.of(CPP);
            case "c17", "c23", "gnu-c", "gcc" -> Optional.of(C);
            case "js", "node", "nodejs", "node.js" -> Optional.of(JAVASCRIPT);
            case "ts", "deno", "typescript-deno", "ts-deno" -> Optional.of(TYPESCRIPT);
            case "golang", "go1.26", "go-1" -> Optional.of(GO);
            case "rs", "rustc", "rust1.93" -> Optional.of(RUST);
            default -> Optional.empty();
        };
    }

    public static String supportedIds() {
        StringBuilder builder = new StringBuilder();
        for (Language language : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(language.id);
        }
        return builder.toString();
    }

    public static List<Language> all() {
        return List.of(values());
    }

    /**
     * Recognises compiler/parser diagnostics for this language. Runtime
     * failures must not match: they are classified as RE by the execution
     * service.
     */
    public boolean looksLikeCompilationError(String combinedOutput) {
        if (combinedOutput == null || combinedOutput.isBlank()) {
            return false;
        }
        String output = combinedOutput;
        String lower = output.toLowerCase(Locale.ROOT);
        return switch (this) {
            case JAVA -> output.contains("compilation failed")
                    || Pattern.compile("(?m)\\.java:\\d+:\\s*(?:error|warning):").matcher(output).find()
                    || (lower.contains("error:") && lower.contains(".java:"));
            case PYTHON -> output.contains("SyntaxError")
                    || output.contains("IndentationError")
                    || output.contains("TabError");
            case CPP -> Pattern.compile("(?m)^.*\\.(?:cc|cpp|cxx|hpp|h):\\d+:\\d+:\\s*(?:fatal\\s+)?error:")
                            .matcher(output).find()
                    || lower.contains("compilation terminated");
            case C -> Pattern.compile("(?m)^.*\\.c:\\d+:\\d+:\\s*(?:fatal\\s+)?error:")
                            .matcher(output).find()
                    || lower.contains("compilation terminated");
            case GO -> Pattern.compile("(?m)^(?:#\\s+command-line-arguments|\\.?/?code\\.go:\\d+:\\d+:)")
                            .matcher(output).find()
                    || lower.contains("syntax error");
            case RUST -> Pattern.compile("(?m)^error(?:\\[E\\d+\\])?:")
                            .matcher(output).find()
                    || Pattern.compile("(?m)^\\s*-->\\s+code\\.rs:").matcher(output).find()
                    || lower.contains("aborting due to");
            case JAVASCRIPT -> Pattern.compile("(?m)^(?:code\\.js:\\d+|SyntaxError:)").matcher(output).find()
                    || output.contains("Unexpected token");
            case TYPESCRIPT -> output.contains("SyntaxError")
                    || Pattern.compile("(?m)^error:\\s+(?:Expected|Unexpected|Invalid|Unterminated|Missing)")
                            .matcher(output).find()
                    || Pattern.compile("(?m)at\\s+file:///.*code\\.ts:\\d+:\\d+").matcher(output).find();
        };
    }

    /** Language-specific hints that the provider killed the run for memory. */
    public boolean mentionsMemoryLimit(String combinedOutput) {
        if (combinedOutput == null || combinedOutput.isBlank()) {
            return false;
        }
        String lower = combinedOutput.toLowerCase(Locale.ROOT);
        return switch (this) {
            case JAVA -> lower.contains("outofmemoryerror")
                    || lower.contains("java heap space")
                    || lower.contains("gc overhead limit exceeded")
                    || lower.contains("native memory allocation");
            case PYTHON -> lower.contains("memoryerror")
                    || lower.contains("cannot allocate memory");
            case CPP, C -> lower.contains("std::bad_alloc")
                    || lower.contains("bad_alloc")
                    || lower.contains("out of memory")
                    || lower.contains("cannot allocate memory");
            case GO -> lower.contains("out of memory")
                    || lower.contains("runtime: out of memory");
            case RUST -> lower.contains("memory allocation of")
                    || lower.contains("out of memory");
            case JAVASCRIPT, TYPESCRIPT -> lower.contains("javascript heap out of memory")
                    || lower.contains("heap out of memory")
                    || lower.contains("array buffer allocation failed");
        };
    }
}
