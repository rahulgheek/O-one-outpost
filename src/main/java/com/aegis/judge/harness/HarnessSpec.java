package com.aegis.judge.harness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Language-neutral problem description used by the judge harness generators.
 *
 * <p>The arena and assessment services store a method-style problem: one
 * method name, a return type, parameter types and test-case literals. This
 * class validates that shape and renders the literals as native source for
 * each target language, so the remote harness never has to parse the input at
 * runtime.</p>
 *
 * <p>Supported types are deliberately closed. They match the constraints
 * already enforced by the assessment problem builder, which keeps every
 * language's harness small and unambiguous, including C and Rust.</p>
 */
public final class HarnessSpec {

    /** Types the judge can render and compare. */
    public enum Kind {
        INT, LONG, DOUBLE, BOOLEAN, STRING,
        INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, STRING_ARRAY;

        public boolean isArray() {
            return ordinal() >= INT_ARRAY.ordinal();
        }
    }

    /** Method shape shared by every language. */
    public record Signature(String methodName, String returnType, List<String> parameterTypes) {
        public Signature {
            if (methodName == null || !methodName.trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException(
                        "Method name must be a valid identifier shared by all languages, got '"
                                + methodName + "'.");
            }
            methodName = methodName.trim();
            if (returnType == null || returnType.isBlank()) {
                throw new IllegalArgumentException("Return type is required.");
            }
            returnType = returnType.trim();
            kind(returnType);

            if (parameterTypes == null) {
                throw new IllegalArgumentException("Parameter type list cannot be null.");
            }
            List<String> normalized = new ArrayList<>(parameterTypes.size());
            for (String parameterType : parameterTypes) {
                if (parameterType == null || parameterType.isBlank()) {
                    throw new IllegalArgumentException("Parameter types cannot be blank.");
                }
                Kind parameterKind = kind(parameterType);
                if (parameterKind.isArray() && parameterKind != Kind.INT_ARRAY) {
                    throw new IllegalArgumentException("Parameter type '" + parameterType.trim()
                            + "' is not supported. Only int[] arrays can be parameters.");
                }
                normalized.add(parameterType.trim());
            }
            parameterTypes = List.copyOf(normalized);
        }

        public Kind returnKind() {
            return kind(returnType);
        }

        public List<Kind> parameterKinds() {
            return parameterTypes.stream().map(HarnessSpec::kind).toList();
        }
    }

    public record TestCase(List<String> inputs, String expectedOutput) {
        public TestCase {
            if (inputs == null) {
                throw new IllegalArgumentException("Test case inputs cannot be null.");
            }
            inputs = List.copyOf(inputs);
            if (expectedOutput == null) {
                throw new IllegalArgumentException("Each test case needs an expected output.");
            }
        }
    }

    public record Spec(Signature signature, List<TestCase> testCases) {
        public Spec {
            if (signature == null) {
                throw new IllegalArgumentException("A method signature is required.");
            }
            if (testCases == null || testCases.isEmpty()) {
                throw new IllegalArgumentException("At least one test case is required.");
            }
            List<TestCase> normalized = new ArrayList<>(testCases.size());
            for (TestCase testCase : testCases) {
                if (testCase == null) {
                    throw new IllegalArgumentException("Test cases cannot be null.");
                }
                if (testCase.inputs().size() != signature.parameterTypes().size()) {
                    throw new IllegalArgumentException(
                            "Each test case must provide exactly one input per method parameter.");
                }
                if (testCase.inputs().stream().anyMatch(input -> input == null)) {
                    throw new IllegalArgumentException("Test case inputs cannot be null.");
                }
                normalized.add(testCase);
            }
            testCases = List.copyOf(normalized);
        }
    }

    private HarnessSpec() {
    }

    public static Kind kind(String rawType) {
        String type = rawType == null ? "" : rawType.replaceAll("\\s+", "");
        return switch (type) {
            case "int" -> Kind.INT;
            case "long" -> Kind.LONG;
            case "double" -> Kind.DOUBLE;
            case "boolean" -> Kind.BOOLEAN;
            case "String" -> Kind.STRING;
            case "int[]" -> Kind.INT_ARRAY;
            case "long[]" -> Kind.LONG_ARRAY;
            case "double[]" -> Kind.DOUBLE_ARRAY;
            case "boolean[]" -> Kind.BOOLEAN_ARRAY;
            case "String[]" -> Kind.STRING_ARRAY;
            default -> throw new IllegalArgumentException("Unsupported type '" + rawType
                    + "'. Supported types: int, long, double, boolean, String, int[], long[], "
                    + "double[], boolean[], String[].");
        };
    }

    /**
     * Parses a stored test-case literal into a Java value. Numbers become
     * Long/Double, quoted values become String, bracketed values become a
     * List, and {@code null} becomes null.
     */
    public static Object parseLiteral(String raw, Kind kind) {
        String text = raw == null ? "" : raw.trim();
        return switch (kind) {
            case INT, LONG -> parseLong(text);
            case DOUBLE -> parseDouble(text);
            case BOOLEAN -> parseBoolean(text);
            case STRING -> unquote(text);
            case INT_ARRAY -> parseIntArray(text);
            case LONG_ARRAY -> parseLongArray(text);
            case DOUBLE_ARRAY -> parseDoubleArray(text);
            case BOOLEAN_ARRAY -> parseBooleanArray(text);
            case STRING_ARRAY -> parseStringArray(text);
        };
    }

    /**
     * Canonical textual form used for comparison. Doubles are normalised to at
     * most six decimals with trailing zeros removed, so a Java "2.0", a
     * JavaScript "2" and a C++ "2.000000" all compare equal. Other values keep
     * the platform's historical whitespace-stripped form.
     */
    public static String expectedFor(Kind kind, String expected) {
        if (expected == null) {
            return "";
        }
        return switch (kind) {
            case DOUBLE -> {
                try {
                    yield formatDouble(parseDouble(expected.trim()));
                } catch (IllegalArgumentException e) {
                    yield expected.replaceAll("\\s+", "");
                }
            }
            case DOUBLE_ARRAY -> {
                try {
                    List<Object> values = parseDoubleArray(expected);
                    StringBuilder builder = new StringBuilder("[");
                    for (int i = 0; i < values.size(); i++) {
                        if (i > 0) {
                            builder.append(',');
                        }
                        builder.append(formatDouble((Double) values.get(i)));
                    }
                    yield builder.append(']').toString();
                } catch (IllegalArgumentException e) {
                    yield expected.replaceAll("\\s+", "");
                }
            }
            default -> expected.replaceAll("\\s+", "");
        };
    }

    public static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    public static boolean isIntegral(Object value) {
        return value instanceof Long;
    }

    /**
     * Escapes a value as a double-quoted string literal. The result is valid in
     * Java, C, C++, Go, Rust, JavaScript, TypeScript and Python.
     */
    public static String quoted(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private static Long parseLong(String text) {
        String value = unquote(text).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing integer value.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value '" + text + "'.", e);
        }
    }

    private static Double parseDouble(String text) {
        String value = unquote(text).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing decimal value.");
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value '" + text + "'.", e);
        }
    }

    private static Boolean parseBoolean(String text) {
        String value = unquote(text).trim();
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean value '" + text + "'.");
    }

    private static List<Object> parseIntArray(String text) {
        List<String> parts = splitArray(text);
        List<Object> values = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (!part.isBlank()) {
                values.add(parseLong(part));
            }
        }
        return values;
    }

    private static List<Object> parseLongArray(String text) {
        return parseIntArray(text);
    }

    private static List<Object> parseDoubleArray(String text) {
        List<String> parts = splitArray(text);
        List<Object> values = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (!part.isBlank()) {
                values.add(parseDouble(part));
            }
        }
        return values;
    }

    private static List<Object> parseBooleanArray(String text) {
        List<String> parts = splitArray(text);
        List<Object> values = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (!part.isBlank()) {
                values.add(parseBoolean(part));
            }
        }
        return values;
    }

    private static List<Object> parseStringArray(String text) {
        List<String> parts = splitArray(text);
        List<Object> values = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (!part.isBlank()) {
                values.add(unquote(part.trim()));
            }
        }
        return values;
    }

    private static List<String> splitArray(String text) {
        String value = text.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        if (!(value.startsWith("[") && value.endsWith("]"))) {
            throw new IllegalArgumentException("Array values must look like [a,b,c], got '" + text + "'.");
        }
        String inner = value.substring(1, value.length() - 1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (c == ',') {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());

        // Also accept whitespace-separated values inside brackets, which the
        // legacy Java parser allowed (for example "[1 2]").
        if (parts.size() == 1 && !parts.get(0).contains("\"") && !parts.get(0).contains("'")) {
            String only = parts.get(0);
            if (only.chars().anyMatch(Character::isWhitespace)) {
                parts = List.of(only.trim().split("\\s+"));
            }
        }
        return parts;
    }

    private static String unquote(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (!((first == '"' && last == '"') || (first == '\'' && last == '\''))) {
            return value;
        }
        String inner = value.substring(1, value.length() - 1);
        StringBuilder builder = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c != '\\' || i + 1 >= inner.length()) {
                builder.append(c);
                continue;
            }
            char next = inner.charAt(++i);
            switch (next) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case '\\' -> builder.append('\\');
                case '"' -> builder.append('"');
                case '\'' -> builder.append('\'');
                case 'u' -> {
                    if (i + 4 < inner.length()) {
                        try {
                            builder.append((char) Integer.parseInt(inner.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            builder.append("\\u");
                        }
                    } else {
                        builder.append("\\u");
                    }
                }
                default -> builder.append('\\').append(next);
            }
        }
        return builder.toString();
    }
}
