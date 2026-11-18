package com.aegis.judge.harness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the starter code a candidate sees for every supported language.
 *
 * <p>Precedence per language:</p>
 * <ol>
 *   <li>an explicit override stored on the problem;</li>
 *   <li>the legacy {@code startingCodeJava} value, for Java only;</li>
 *   <li>a generated stub derived from the stored method signature;</li>
 *   <li>a language-neutral placeholder for malformed legacy problems.</li>
 * </ol>
 *
 * <p>Mirrored in arena-service and assessment-service. Keep the copies
 * identical.</p>
 */
public final class StarterCode {

    private StarterCode() {
    }

    public static Map<String, String> forProblem(Map<String, String> overrides,
                                                 String startingCodeJava,
                                                 String methodName,
                                                 String returnType,
                                                 List<String> parameterTypes) {
        Map<String, String> resolved = new LinkedHashMap<>();
        HarnessSpec.Signature signature = null;
        try {
            signature = new HarnessSpec.Signature(methodName, returnType, parameterTypes);
        } catch (RuntimeException ignored) {
            // A legacy or malformed problem still gets a usable editor.
        }

        for (HarnessLanguage language : HarnessLanguage.values()) {
            String explicit = overrides == null ? null : overrides.get(language.id());
            if (explicit != null && !explicit.isBlank()) {
                resolved.put(language.id(), explicit);
            } else if (language == HarnessLanguage.JAVA
                    && startingCodeJava != null && !startingCodeJava.isBlank()) {
                resolved.put(language.id(), startingCodeJava);
            } else if (signature != null) {
                resolved.put(language.id(), HarnessGenerator.starterCode(language, signature));
            } else {
                resolved.put(language.id(), placeholder(language));
            }
        }
        return resolved;
    }

    private static String placeholder(HarnessLanguage language) {
        return switch (language) {
            case JAVA -> "class Solution {\n    // Write your solution here\n}";
            case PYTHON -> "class Solution:\n    # Write your solution here\n    pass";
            case CPP -> "class Solution {\npublic:\n    // Write your solution here\n};";
            case C -> "// Write your solution here\n";
            case JAVASCRIPT -> "class Solution {\n    // Write your solution here\n}";
            case TYPESCRIPT -> "class Solution {\n    // Write your solution here\n}";
            case GO -> "// Write your solution here\n";
            case RUST -> "// Write your solution here\n";
        };
    }
}
