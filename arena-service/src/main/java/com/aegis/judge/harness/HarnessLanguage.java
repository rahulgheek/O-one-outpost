package com.aegis.judge.harness;

import java.util.Locale;

/**
 * Languages for which the judge can generate a native method harness.
 *
 * <p>The ids match {@code com.aegis.code_sandbox.judge.Language}. They are
 * duplicated here because arena-service and assessment-service are independent
 * Maven modules with their own Docker build contexts; this package is mirrored
 * in both services.</p>
 */
public enum HarnessLanguage {

    JAVA("java", "Java", "java"),
    PYTHON("python", "Python", "python"),
    CPP("cpp", "C++", "cpp"),
    C("c", "C", "c"),
    JAVASCRIPT("javascript", "JavaScript", "javascript"),
    TYPESCRIPT("typescript", "TypeScript", "typescript"),
    GO("go", "Go", "go"),
    RUST("rust", "Rust", "rust");

    private final String id;
    private final String displayName;
    private final String editorLanguage;

    HarnessLanguage(String id, String displayName, String editorLanguage) {
        this.id = id;
        this.displayName = displayName;
        this.editorLanguage = editorLanguage;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String editorLanguage() {
        return editorLanguage;
    }

    public static HarnessLanguage resolve(String value) {
        if (value == null || value.isBlank()) {
            return JAVA;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (HarnessLanguage language : values()) {
            if (language.id.equals(normalized)) {
                return language;
            }
        }
        return switch (normalized) {
            case "py", "python3" -> PYTHON;
            case "c++", "cplusplus" -> CPP;
            case "js", "node" -> JAVASCRIPT;
            case "ts", "deno" -> TYPESCRIPT;
            case "golang" -> GO;
            case "rs" -> RUST;
            default -> throw new IllegalArgumentException(
                    "Unsupported language '" + value + "'. Supported languages: " + supportedIds() + ".");
        };
    }

    public static String supportedIds() {
        StringBuilder builder = new StringBuilder();
        for (HarnessLanguage language : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(language.id);
        }
        return builder.toString();
    }
}
