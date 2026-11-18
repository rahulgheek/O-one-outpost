package com.aegis.judge.harness;

import java.util.List;

/**
 * Generates a complete, runnable judge harness for a method-style problem in
 * one of eight languages.
 *
 * <p>Test inputs are rendered as native literals at generation time. The
 * remote program therefore only has to call the contestant's method, format
 * the result and print the shared line protocol. No language needs a runtime
 * parser, which keeps every harness small and free of parsing edge cases.</p>
 *
 * <p>Protocol:</p>
 * <ul>
 *   <li>{@link Protocol#ARENA}: {@code PROGRESS|passed/total} followed by either
 *       {@code ALL_TESTS_PASSED} or the first {@code WRONG_ANSWER} /
 *       {@code RUNTIME_ERROR} line.</li>
 *   <li>{@link Protocol#ASSESSMENT}: per-test failure lines followed by
 *       {@code ASSESSMENT_RESULT|passed|total}, so partial credit survives.</li>
 * </ul>
 *
 * <p>C and Rust are supported because the platform limits parameter types to
 * scalars and {@code int[]}. C uses the LeetCode convention for array returns:
 * the generated signature appends an {@code int* returnSize} out-parameter.
 * Rust methods are associated functions ({@code Solution::name(...)}), matching
 * the LeetCode Rust shape.</p>
 *
 * <p>This class is mirrored in arena-service and assessment-service. Keep the
 * copies identical.</p>
 */
public final class HarnessGenerator {

    public enum Protocol {
        /** Emits PROGRESS once and ALL_TESTS_PASSED on success. */
        ARENA,
        /** Emits ASSESSMENT_RESULT with the partial score. */
        ASSESSMENT
    }

    private HarnessGenerator() {
    }

    public static String generate(HarnessLanguage language, String userCode,
                                  HarnessSpec.Spec spec, Protocol protocol) {
        String code = userCode == null ? "" : userCode;
        return switch (language) {
            case JAVA -> java(code, spec, protocol);
            case PYTHON -> python(code, spec, protocol);
            case CPP -> cpp(code, spec, protocol);
            case C -> c(code, spec, protocol);
            case JAVASCRIPT -> javascript(code, spec, protocol, false);
            case TYPESCRIPT -> javascript(code, spec, protocol, true);
            case GO -> go(code, spec, protocol);
            case RUST -> rust(code, spec, protocol);
        };
    }

    /** Returns true when every type in the signature is renderable. */
    public static boolean supports(HarnessSpec.Signature signature) {
        try {
            signature.returnKind();
            signature.parameterKinds();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // =====================================================================
    // Java
    // =====================================================================

    private static String java(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(4_096);
        line(builder, "import java.util.*;");
        blank(builder);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "public class Main {");
        line(builder, "    static String canonDouble(double v) {");
        line(builder, "        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);");
        line(builder, "        return java.math.BigDecimal.valueOf(v).setScale(6, java.math.RoundingMode.HALF_UP)"
                + ".stripTrailingZeros().toPlainString();");
        line(builder, "    }");
        line(builder, "    static String fmt(Object obj) {");
        line(builder, "        if (obj == null) return \"null\";");
        line(builder, "        if (obj instanceof Double) return canonDouble((Double) obj);");
        line(builder, "        if (obj instanceof Float) return canonDouble(((Float) obj).doubleValue());");
        line(builder, "        if (obj instanceof int[]) return Arrays.toString((int[]) obj)"
                + ".replaceAll(\"\\\\s+\", \"\");");
        line(builder, "        if (obj instanceof long[]) return Arrays.toString((long[]) obj)"
                + ".replaceAll(\"\\\\s+\", \"\");");
        line(builder, "        if (obj instanceof double[]) {");
        line(builder, "            double[] values = (double[]) obj;");
        line(builder, "            StringBuilder out = new StringBuilder(\"[\");");
        line(builder, "            for (int i = 0; i < values.length; i++) {");
        line(builder, "                if (i > 0) out.append(',');");
        line(builder, "                out.append(canonDouble(values[i]));");
        line(builder, "            }");
        line(builder, "            return out.append(']').toString();");
        line(builder, "        }");
        line(builder, "        if (obj instanceof boolean[]) return Arrays.toString((boolean[]) obj)"
                + ".replaceAll(\"\\\\s+\", \"\");");
        line(builder, "        if (obj instanceof String[]) return Arrays.toString((String[]) obj)"
                + ".replaceAll(\"\\\\s+\", \"\");");
        line(builder, "        if (obj instanceof Object[]) return Arrays.deepToString((Object[]) obj)"
                + ".replaceAll(\"\\\\s+\", \"\");");
        line(builder, "        return String.valueOf(obj).replaceAll(\"\\\\s+\", \"\");");
        line(builder, "    }");
        line(builder, "    public static void main(String[] args) {");
        line(builder, "        Solution solution = new Solution();");
        line(builder, "        int passed = 0;");
        line(builder, "        int total = " + spec.testCases().size() + ";");
        if (protocol == Protocol.ARENA) {
            line(builder, "        String firstError = null;");
        }

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            String expectedLiteral = HarnessSpec.quoted(expected);
            line(builder, "        // Test case " + (t + 1));
            line(builder, "        try {");
            for (int i = 0; i < parameters.size(); i++) {
                HarnessSpec.Kind kind = parameters.get(i);
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), kind);
                line(builder, "            " + javaType(kind) + " arg" + t + "_" + i + " = "
                        + javaLiteral(kind, value) + ";");
            }
            StringBuilder call = new StringBuilder("solution.").append(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                call.append("arg").append(t).append('_').append(i);
            }
            call.append(')');
            line(builder, "            " + javaType(returnKind) + " actual = " + call + ";");
            line(builder, "            String got = fmt(actual);");
            line(builder, "            if (got.equals(" + expectedLiteral + ")) {");
            line(builder, "                passed++;");
            if (protocol == Protocol.ARENA) {
                line(builder, "            } else if (firstError == null) {");
                line(builder, "                firstError = \"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: \" + " + expectedLiteral + " + \", Got: \" + got;");
            } else {
                line(builder, "            } else {");
                line(builder, "                System.out.println(\"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: \" + " + expectedLiteral + " + \", Got: \" + got);");
            }
            line(builder, "            }");
            line(builder, "        } catch (Exception e) {");
            if (protocol == Protocol.ARENA) {
                line(builder, "            if (firstError == null) {");
                line(builder, "                firstError = \"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": \" + e.getClass().getSimpleName()"
                        + " + (e.getMessage() == null ? \"\" : \": \" + e.getMessage());");
                line(builder, "            }");
            } else {
                line(builder, "            System.out.println(\"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": \" + e.getClass().getSimpleName()"
                        + " + (e.getMessage() == null ? \"\" : \": \" + e.getMessage()));");
            }
            line(builder, "        }");
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "        System.out.println(\"PROGRESS|\" + passed + \"/\" + total);");
            line(builder, "        if (firstError != null) {");
            line(builder, "            System.out.println(firstError);");
            line(builder, "        } else {");
            line(builder, "            System.out.println(\"ALL_TESTS_PASSED\");");
            line(builder, "        }");
        } else {
            line(builder, "");
            line(builder, "        System.out.println(\"\\nASSESSMENT_RESULT|\" + passed + \"|\" + total);");
        }
        line(builder, "    }");
        line(builder, "}");
        return builder.toString();
    }

    private static String javaType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "int";
            case LONG -> "long";
            case DOUBLE -> "double";
            case BOOLEAN -> "boolean";
            case STRING -> "String";
            case INT_ARRAY -> "int[]";
            case LONG_ARRAY -> "long[]";
            case DOUBLE_ARRAY -> "double[]";
            case BOOLEAN_ARRAY -> "boolean[]";
            case STRING_ARRAY -> "String[]";
        };
    }

    private static String javaLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT -> {
                long number = (Long) value;
                if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Integer value " + number + " is out of range.");
                }
                yield Long.toString(number);
            }
            case LONG -> value + "L";
            case DOUBLE -> decimal(value) + "d";
            case BOOLEAN -> value.toString();
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> "new int[]{" + joinInts(values(value)) + "}";
            case LONG_ARRAY -> "new long[]{" + joinLongs(values(value)) + "}";
            case DOUBLE_ARRAY -> "new double[]{" + joinDoubles(values(value)) + "}";
            case BOOLEAN_ARRAY -> "new boolean[]{" + joinBooleans(values(value)) + "}";
            case STRING_ARRAY -> "new String[]{" + joinStrings(values(value)) + "}";
        };
    }

    // =====================================================================
    // Python
    // =====================================================================

    private static String python(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(4_096);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "import sys");
        blank(builder);
        line(builder, "sys.setrecursionlimit(20000)");
        blank(builder);
        line(builder, "def _canon(value):");
        line(builder, "    if value != value or value in (float('inf'), float('-inf')):");
        line(builder, "        return str(value).lower()");
        line(builder, "    text = '%.6f' % value");
        line(builder, "    if '.' in text:");
        line(builder, "        text = text.rstrip('0').rstrip('.')");
        line(builder, "    if text in ('-0', ''):");
        line(builder, "        text = '0'");
        line(builder, "    return text");
        blank(builder);
        line(builder, "_WHITESPACE = {ord(c): None for c in ' \\t\\r\\n\\f\\v'}");
        blank(builder);
        line(builder, "def _fmt(value):");
        line(builder, "    if value is None:");
        line(builder, "        return 'null'");
        line(builder, "    if isinstance(value, bool):");
        line(builder, "        return 'true' if value else 'false'");
        line(builder, "    if isinstance(value, float):");
        line(builder, "        return _canon(value)");
        line(builder, "    if isinstance(value, (list, tuple)):");
        line(builder, "        return '[' + ','.join(_fmt(item) for item in value) + ']'");
        line(builder, "    return str(value).translate(_WHITESPACE)");
        blank(builder);
        line(builder, "def _main():");
        line(builder, "    solution = Solution()");
        line(builder, "    passed = 0");
        line(builder, "    total = " + spec.testCases().size());
        if (protocol == Protocol.ARENA) {
            line(builder, "    first_error = None");
        }

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            String expectedLiteral = HarnessSpec.quoted(expected);
            line(builder, "    # Test case " + (t + 1));
            line(builder, "    try:");
            StringBuilder call = new StringBuilder("solution.").append(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(pythonLiteral(parameters.get(i), value));
            }
            call.append(')');
            line(builder, "        actual = " + call);
            line(builder, "        got = _fmt(actual)");
            line(builder, "        if got == " + expectedLiteral + ":");
            line(builder, "            passed += 1");
            if (protocol == Protocol.ARENA) {
                line(builder, "        elif first_error is None:");
                line(builder, "            first_error = 'WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: ' + " + expectedLiteral + " + ', Got: ' + got");
            } else {
                line(builder, "        else:");
                line(builder, "            print('WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: ' + " + expectedLiteral + " + ', Got: ' + got)");
            }
            line(builder, "    except Exception as error:");
            if (protocol == Protocol.ARENA) {
                line(builder, "        if first_error is None:");
                line(builder, "            first_error = 'RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": ' + type(error).__name__ + (': ' + str(error) if str(error) else '')");
            } else {
                line(builder, "        print('RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": ' + type(error).__name__ + (': ' + str(error) if str(error) else ''))");
            }
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "    print('PROGRESS|' + str(passed) + '/' + str(total))");
            line(builder, "    if first_error is not None:");
            line(builder, "        print(first_error)");
            line(builder, "    else:");
            line(builder, "        print('ALL_TESTS_PASSED')");
        } else {
            line(builder, "    print('ASSESSMENT_RESULT|' + str(passed) + '|' + str(total))");
        }
        blank(builder);
        line(builder, "_main()");
        return builder.toString();
    }

    private static String pythonLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT, LONG -> value.toString();
            case DOUBLE -> decimal(value);
            case BOOLEAN -> (Boolean) value ? "True" : "False";
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> "[" + joinInts(values(value)) + "]";
            case LONG_ARRAY -> "[" + joinLongsRaw(values(value)) + "]";
            case DOUBLE_ARRAY -> "[" + joinDoubles(values(value)) + "]";
            case BOOLEAN_ARRAY -> "[" + values(value).stream()
                    .map(item -> (Boolean) item ? "True" : "False")
                    .reduce((a, b) -> a + ", " + b).orElse("") + "]";
            case STRING_ARRAY -> "[" + joinStrings(values(value)) + "]";
        };
    }

    // =====================================================================
    // C++
    // =====================================================================

    private static String cpp(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(6_000);
        line(builder, "#include <bits/stdc++.h>");
        line(builder, "using namespace std;");
        blank(builder);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "static string judgeCanon(double v) {");
        line(builder, "    if (isnan(v) || isinf(v)) { ostringstream os; os << v; return os.str(); }");
        line(builder, "    ostringstream os;");
        line(builder, "    os << fixed << setprecision(6) << v;");
        line(builder, "    string s = os.str();");
        line(builder, "    if (s.find('.') != string::npos) {");
        line(builder, "        while (!s.empty() && s.back() == '0') s.pop_back();");
        line(builder, "        if (!s.empty() && s.back() == '.') s.pop_back();");
        line(builder, "    }");
        line(builder, "    if (s.empty() || s == \"-0\") s = \"0\";");
        line(builder, "    return s;");
        line(builder, "}");
        line(builder, "static string judgeFmt(int v) { return to_string(v); }");
        line(builder, "static string judgeFmt(long long v) { return to_string(v); }");
        line(builder, "static string judgeFmt(double v) { return judgeCanon(v); }");
        line(builder, "static string judgeFmt(bool v) { return v ? \"true\" : \"false\"; }");
        line(builder, "static string judgeFmt(const string& v) {");
        line(builder, "    string out;");
        line(builder, "    for (char c : v) if (!isspace(static_cast<unsigned char>(c))) out.push_back(c);");
        line(builder, "    return out;");
        line(builder, "}");
        emitCppVectorFormatter(builder, "int", "judgeFmt(v[i])");
        emitCppVectorFormatter(builder, "long long", "judgeFmt(v[i])");
        emitCppVectorFormatter(builder, "double", "judgeFmt(v[i])");
        emitCppVectorFormatter(builder, "bool", "judgeFmt(v[i])");
        emitCppVectorFormatter(builder, "string", "judgeFmt(v[i])");
        blank(builder);
        line(builder, "int main() {");
        line(builder, "    Solution solution;");
        line(builder, "    int passed = 0;");
        line(builder, "    int total = " + spec.testCases().size() + ";");
        if (protocol == Protocol.ARENA) {
            line(builder, "    string firstError;");
            line(builder, "    bool hasError = false;");
        }

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            line(builder, "    // Test case " + (t + 1));
            line(builder, "    try {");
            StringBuilder call = new StringBuilder("solution.").append(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(cppLiteral(parameters.get(i), value));
            }
            call.append(')');
            line(builder, "        " + cppType(returnKind) + " actual = " + call + ";");
            line(builder, "        string got = judgeFmt(actual);");
            line(builder, "        if (got == " + HarnessSpec.quoted(expected) + ") { passed++; }");
            if (protocol == Protocol.ARENA) {
                line(builder, "        else if (!hasError) {");
                line(builder, "            hasError = true;");
                line(builder, "            firstError = string(\"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: \") + " + HarnessSpec.quoted(expected)
                        + " + \", Got: \" + got;");
                line(builder, "        }");
            } else {
                line(builder, "        else { cout << \"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: \" << " + HarnessSpec.quoted(expected)
                        + " << \", Got: \" << got << endl; }");
            }
            line(builder, "    } catch (const exception& error) {");
            if (protocol == Protocol.ARENA) {
                line(builder, "        if (!hasError) { hasError = true; firstError = "
                        + "string(\"RUNTIME_ERROR|Test Case " + (t + 1) + ": \") + error.what(); }");
            } else {
                line(builder, "        cout << \"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": \" << error.what() << endl;");
            }
            line(builder, "    } catch (...) {");
            if (protocol == Protocol.ARENA) {
                line(builder, "        if (!hasError) { hasError = true; firstError = "
                        + "\"RUNTIME_ERROR|Test Case " + (t + 1) + ": unknown error\"; }");
            } else {
                line(builder, "        cout << \"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": unknown error\" << endl;");
            }
            line(builder, "    }");
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "    cout << \"PROGRESS|\" << passed << \"/\" << total << endl;");
            line(builder, "    if (hasError) cout << firstError << endl;");
            line(builder, "    else cout << \"ALL_TESTS_PASSED\" << endl;");
        } else {
            line(builder, "    cout << \"ASSESSMENT_RESULT|\" << passed << \"|\" << total << endl;");
        }
        line(builder, "    return 0;");
        line(builder, "}");
        return builder.toString();
    }

    private static void emitCppVectorFormatter(StringBuilder builder, String type, String expression) {
        line(builder, "static string judgeFmt(const vector<" + type + ">& v) {");
        line(builder, "    string out = \"[\";");
        line(builder, "    for (size_t i = 0; i < v.size(); ++i) { if (i) out += ','; out += "
                + expression + "; }");
        line(builder, "    return out + \"]\";");
        line(builder, "}");
    }

    private static String cppType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "int";
            case LONG -> "long long";
            case DOUBLE -> "double";
            case BOOLEAN -> "bool";
            case STRING -> "string";
            case INT_ARRAY -> "vector<int>";
            case LONG_ARRAY -> "vector<long long>";
            case DOUBLE_ARRAY -> "vector<double>";
            case BOOLEAN_ARRAY -> "vector<bool>";
            case STRING_ARRAY -> "vector<string>";
        };
    }

    private static String cppLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT -> value.toString();
            case LONG -> value + "LL";
            case DOUBLE -> decimal(value);
            case BOOLEAN -> value.toString();
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> "vector<int>{" + joinInts(values(value)) + "}";
            case LONG_ARRAY -> "vector<long long>{" + joinLongs(values(value)) + "}";
            case DOUBLE_ARRAY -> "vector<double>{" + joinDoubles(values(value)) + "}";
            case BOOLEAN_ARRAY -> "vector<bool>{" + joinBooleans(values(value)) + "}";
            case STRING_ARRAY -> "vector<string>{" + joinStrings(values(value)) + "}";
        };
    }

    // =====================================================================
    // C
    // =====================================================================

    private static String c(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(8_000);
        line(builder, "#include <stdio.h>");
        line(builder, "#include <stdlib.h>");
        line(builder, "#include <string.h>");
        line(builder, "#include <stdbool.h>");
        line(builder, "#include <ctype.h>");
        blank(builder);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "#define JUDGE_BUF_COUNT 8");
        line(builder, "#define JUDGE_BUF_SIZE 8192");
        line(builder, "static char judge_buffers[JUDGE_BUF_COUNT][JUDGE_BUF_SIZE];");
        line(builder, "static int judge_buffer_index = 0;");
        line(builder, "static char* judge_next(void) {");
        line(builder, "    char* buffer = judge_buffers[judge_buffer_index];");
        line(builder, "    judge_buffer_index = (judge_buffer_index + 1) % JUDGE_BUF_COUNT;");
        line(builder, "    buffer[0] = '\\0';");
        line(builder, "    return buffer;");
        line(builder, "}");
        line(builder, "static void judge_append(char* out, size_t* used, const char* text) {");
        line(builder, "    size_t length = strlen(text);");
        line(builder, "    if (*used + length >= JUDGE_BUF_SIZE) length = JUDGE_BUF_SIZE - 1 - *used;");
        line(builder, "    memcpy(out + *used, text, length);");
        line(builder, "    *used += length;");
        line(builder, "    out[*used] = '\\0';");
        line(builder, "}");
        line(builder, "static void judge_append_double(char* out, size_t* used, double value) {");
        line(builder, "    char tmp[64];");
        line(builder, "    snprintf(tmp, sizeof(tmp), \"%.6f\", value);");
        line(builder, "    char* dot = strchr(tmp, '.');");
        line(builder, "    if (dot != NULL) {");
        line(builder, "        char* end = tmp + strlen(tmp) - 1;");
        line(builder, "        while (end > dot && *end == '0') { *end = '\\0'; end--; }");
        line(builder, "        if (end == dot) *end = '\\0';");
        line(builder, "    }");
        line(builder, "    if (tmp[0] == '\\0' || strcmp(tmp, \"-0\") == 0) strcpy(tmp, \"0\");");
        line(builder, "    judge_append(out, used, tmp);");
        line(builder, "}");
        line(builder, "static char* judge_fmt_int(int value) { char* out = judge_next(); size_t used = 0;"
                + " char tmp[32]; snprintf(tmp, sizeof(tmp), \"%d\", value); judge_append(out, &used, tmp);"
                + " return out; }");
        line(builder, "static char* judge_fmt_long(long long value) { char* out = judge_next(); size_t used = 0;"
                + " char tmp[32]; snprintf(tmp, sizeof(tmp), \"%lld\", value); judge_append(out, &used, tmp);"
                + " return out; }");
        line(builder, "static char* judge_fmt_double(double value) { char* out = judge_next(); size_t used = 0;"
                + " judge_append_double(out, &used, value); return out; }");
        line(builder, "static char* judge_fmt_bool(bool value) { char* out = judge_next();"
                + " strcpy(out, value ? \"true\" : \"false\"); return out; }");
        line(builder, "static char* judge_fmt_string(char* value) {");
        line(builder, "    if (value == NULL) return \"null\";");
        line(builder, "    char* out = judge_next(); size_t used = 0;");
        line(builder, "    for (size_t i = 0; value[i] != '\\0'; i++) {");
        line(builder, "        unsigned char c = (unsigned char) value[i];");
        line(builder, "        if (!isspace(c)) { char one[2] = { (char) c, '\\0' }; judge_append(out, &used, one); }");
        line(builder, "    }");
        line(builder, "    return out;");
        line(builder, "}");
        line(builder, "static char* judge_fmt_int_array(int* values, int size) {");
        line(builder, "    char* out = judge_next(); size_t used = 0; char tmp[32];");
        line(builder, "    judge_append(out, &used, \"[\");");
        line(builder, "    for (int i = 0; i < size; i++) {");
        line(builder, "        if (i > 0) judge_append(out, &used, \",\");");
        line(builder, "        snprintf(tmp, sizeof(tmp), \"%d\", values[i]);");
        line(builder, "        judge_append(out, &used, tmp);");
        line(builder, "    }");
        line(builder, "    judge_append(out, &used, \"]\");");
        line(builder, "    return out;");
        line(builder, "}");
        line(builder, "static char* judge_fmt_long_array(long long* values, int size) {");
        line(builder, "    char* out = judge_next(); size_t used = 0; char tmp[32];");
        line(builder, "    judge_append(out, &used, \"[\");");
        line(builder, "    for (int i = 0; i < size; i++) {");
        line(builder, "        if (i > 0) judge_append(out, &used, \",\");");
        line(builder, "        snprintf(tmp, sizeof(tmp), \"%lld\", values[i]);");
        line(builder, "        judge_append(out, &used, tmp);");
        line(builder, "    }");
        line(builder, "    judge_append(out, &used, \"]\");");
        line(builder, "    return out;");
        line(builder, "}");
        line(builder, "static char* judge_fmt_double_array(double* values, int size) {");
        line(builder, "    char* out = judge_next(); size_t used = 0;");
        line(builder, "    judge_append(out, &used, \"[\");");
        line(builder, "    for (int i = 0; i < size; i++) {");
        line(builder, "        if (i > 0) judge_append(out, &used, \",\");");
        line(builder, "        judge_append_double(out, &used, values[i]);");
        line(builder, "    }");
        line(builder, "    judge_append(out, &used, \"]\");");
        line(builder, "    return out;");
        line(builder, "}");
        line(builder, "static char* judge_fmt_bool_array(bool* values, int size) {");
        line(builder, "    char* out = judge_next(); size_t used = 0;");
        line(builder, "    judge_append(out, &used, \"[\");");
        line(builder, "    for (int i = 0; i < size; i++) {");
        line(builder, "        if (i > 0) judge_append(out, &used, \",\");");
        line(builder, "        judge_append(out, &used, values[i] ? \"true\" : \"false\");");
        line(builder, "    }");
        line(builder, "    judge_append(out, &used, \"]\");");
        line(builder, "    return out;");
        line(builder, "}");
        line(builder, "static char* judge_fmt_string_array(char** values, int size) {");
        line(builder, "    char* out = judge_next(); size_t used = 0;");
        line(builder, "    judge_append(out, &used, \"[\");");
        line(builder, "    for (int i = 0; i < size; i++) {");
        line(builder, "        if (i > 0) judge_append(out, &used, \",\");");
        line(builder, "        if (values[i] == NULL) { judge_append(out, &used, \"null\"); continue; }");
        line(builder, "        for (size_t j = 0; values[i][j] != '\\0'; j++) {");
        line(builder, "            unsigned char c = (unsigned char) values[i][j];");
        line(builder, "            if (!isspace(c)) { char one[2] = { (char) c, '\\0' }; judge_append(out, &used, one); }");
        line(builder, "        }");
        line(builder, "    }");
        line(builder, "    judge_append(out, &used, \"]\");");
        line(builder, "    return out;");
        line(builder, "}");
        blank(builder);
        line(builder, "int main(void) {");
        line(builder, "    setvbuf(stdout, NULL, _IONBF, 0);");
        line(builder, "    int passed = 0;");
        line(builder, "    int total = " + spec.testCases().size() + ";");
        if (protocol == Protocol.ARENA) {
            line(builder, "    char firstError[4096];");
            line(builder, "    int hasError = 0;");
        }

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            line(builder, "    // Test case " + (t + 1));
            line(builder, "    {");
            if (returnKind.isArray()) {
                line(builder, "        int returnSize = 0;");
            }
            StringBuilder call = new StringBuilder(signature.methodName()).append('(');
            boolean firstArgument = true;
            for (int i = 0; i < parameters.size(); i++) {
                if (!firstArgument) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(cArgument(parameters.get(i), value));
                firstArgument = false;
            }
            if (returnKind.isArray()) {
                if (!firstArgument) {
                    call.append(", ");
                }
                call.append("&returnSize");
            }
            call.append(')');
            line(builder, "        " + cType(returnKind) + " actual = " + call + ";");
            line(builder, "        char* got = " + cFormatter(returnKind, "actual") + ";");
            line(builder, "        if (strcmp(got, " + HarnessSpec.quoted(expected) + ") == 0) { passed++; }");
            if (protocol == Protocol.ARENA) {
                line(builder, "        else if (!hasError) {");
                line(builder, "            hasError = 1;");
                line(builder, "            snprintf(firstError, sizeof(firstError), "
                        + "\"WRONG_ANSWER|Test Case " + (t + 1) + " Failed. Expected: %s, Got: %s\", "
                        + HarnessSpec.quoted(expected) + ", got);");
                line(builder, "        }");
            } else {
                line(builder, "        else { printf(\"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: %s, Got: %s\\n\", " + HarnessSpec.quoted(expected)
                        + ", got); }");
            }
            line(builder, "    }");
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "    printf(\"PROGRESS|%d/%d\\n\", passed, total);");
            line(builder, "    if (hasError) printf(\"%s\\n\", firstError);");
            line(builder, "    else printf(\"ALL_TESTS_PASSED\\n\");");
        } else {
            line(builder, "    printf(\"ASSESSMENT_RESULT|%d|%d\\n\", passed, total);");
        }
        line(builder, "    return 0;");
        line(builder, "}");
        return builder.toString();
    }

    private static String cType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "int";
            case LONG -> "long long";
            case DOUBLE -> "double";
            case BOOLEAN -> "bool";
            case STRING -> "char*";
            case INT_ARRAY -> "int*";
            case LONG_ARRAY -> "long long*";
            case DOUBLE_ARRAY -> "double*";
            case BOOLEAN_ARRAY -> "bool*";
            case STRING_ARRAY -> "char**";
        };
    }

    private static String cFormatter(HarnessSpec.Kind kind, String variable) {
        return switch (kind) {
            case INT -> "judge_fmt_int(" + variable + ")";
            case LONG -> "judge_fmt_long(" + variable + ")";
            case DOUBLE -> "judge_fmt_double(" + variable + ")";
            case BOOLEAN -> "judge_fmt_bool(" + variable + ")";
            case STRING -> "judge_fmt_string(" + variable + ")";
            case INT_ARRAY -> "judge_fmt_int_array(" + variable + ", returnSize)";
            case LONG_ARRAY -> "judge_fmt_long_array(" + variable + ", returnSize)";
            case DOUBLE_ARRAY -> "judge_fmt_double_array(" + variable + ", returnSize)";
            case BOOLEAN_ARRAY -> "judge_fmt_bool_array(" + variable + ", returnSize)";
            case STRING_ARRAY -> "judge_fmt_string_array(" + variable + ", returnSize)";
        };
    }

    private static String cArgument(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT -> value.toString();
            case LONG -> value + "LL";
            case DOUBLE -> decimal(value);
            case BOOLEAN -> (Boolean) value ? "true" : "false";
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> {
                List<Object> items = values(value);
                String elements = items.isEmpty() ? "0" : joinInts(items);
                yield "(int[]){" + elements + "}, " + items.size();
            }
            default -> throw new IllegalArgumentException("Unsupported C parameter type.");
        };
    }

    // =====================================================================
    // JavaScript / TypeScript
    // =====================================================================

    private static String javascript(String userCode, HarnessSpec.Spec spec, Protocol protocol,
                                     boolean typed) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(5_000);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, typed ? "function _canon(value: number): string {"
                : "function _canon(value) {");
        line(builder, "    if (!Number.isFinite(value)) return String(value);");
        line(builder, "    if (Math.abs(value) >= 1e21) return String(value);");
        line(builder, "    let text = value.toFixed(6);");
        line(builder, "    if (text.indexOf('.') >= 0) {");
        line(builder, "        text = text.replace(/0+$/, '').replace(/\\.$/, '');");
        line(builder, "    }");
        line(builder, "    if (text === '-0' || text === '') text = '0';");
        line(builder, "    return text;");
        line(builder, "}");
        line(builder, typed ? "function _fmt(value: unknown): string {"
                : "function _fmt(value) {");
        line(builder, "    if (value === null || value === undefined) return 'null';");
        line(builder, "    if (typeof value === 'boolean') return value ? 'true' : 'false';");
        line(builder, "    if (typeof value === 'number') {");
        line(builder, "        if (Number.isInteger(value)) return String(value);");
        line(builder, "        return _canon(value);");
        line(builder, "    }");
        line(builder, "    if (Array.isArray(value)) return '[' + value.map(_fmt).join(',') + ']';");
        line(builder, "    return String(value).replace(/\\s+/g, '');");
        line(builder, "}");
        line(builder, "(function () {");
        line(builder, "    const solution = new Solution();");
        line(builder, "    let passed = 0;");
        line(builder, "    const total = " + spec.testCases().size() + ";");
        if (protocol == Protocol.ARENA) {
            line(builder, "    let firstError = null;");
        }
        String errorMessage = "(error instanceof Error ? error.message : String(error))";

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            String expectedLiteral = HarnessSpec.quoted(expected);
            line(builder, "    // Test case " + (t + 1));
            line(builder, "    try {");
            StringBuilder call = new StringBuilder("solution.").append(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(javascriptLiteral(parameters.get(i), value));
            }
            call.append(')');
            line(builder, "        const actual = " + call + ";");
            line(builder, "        const got = _fmt(actual);");
            line(builder, "        if (got === " + expectedLiteral + ") {");
            line(builder, "            passed += 1;");
            if (protocol == Protocol.ARENA) {
                line(builder, "        } else if (firstError === null) {");
                line(builder, "            firstError = 'WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: ' + " + expectedLiteral + " + ', Got: ' + got;");
            } else {
                line(builder, "        } else {");
                line(builder, "            console.log('WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: ' + " + expectedLiteral + " + ', Got: ' + got);");
            }
            line(builder, "        }");
            line(builder, "    } catch (error) {");
            if (protocol == Protocol.ARENA) {
                line(builder, "        if (firstError === null) {");
                line(builder, "            firstError = 'RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": ' + " + errorMessage + ";");
                line(builder, "        }");
            } else {
                line(builder, "        console.log('RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": ' + " + errorMessage + ");");
            }
            line(builder, "    }");
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "    console.log('PROGRESS|' + passed + '/' + total);");
            line(builder, "    if (firstError !== null) console.log(firstError);");
            line(builder, "    else console.log('ALL_TESTS_PASSED');");
        } else {
            line(builder, "    console.log('ASSESSMENT_RESULT|' + passed + '|' + total);");
        }
        line(builder, "})();");
        return builder.toString();
    }

    private static String javascriptLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT, LONG -> value.toString();
            case DOUBLE -> decimal(value);
            case BOOLEAN -> value.toString();
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> "[" + joinInts(values(value)) + "]";
            case LONG_ARRAY -> "[" + joinLongsRaw(values(value)) + "]";
            case DOUBLE_ARRAY -> "[" + joinDoubles(values(value)) + "]";
            case BOOLEAN_ARRAY -> "[" + joinBooleans(values(value)) + "]";
            case STRING_ARRAY -> "[" + joinStrings(values(value)) + "]";
        };
    }

    // =====================================================================
    // Go
    // =====================================================================

    private static String go(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(6_000);
        line(builder, "package main");
        blank(builder);
        line(builder, "import (");
        line(builder, "\t\"fmt\"");
        line(builder, "\t\"strconv\"");
        line(builder, "\t\"strings\"");
        line(builder, ")");
        blank(builder);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "func judgeCanon(value float64) string {");
        line(builder, "\tif value != value || value > 1.7976931348623157e308 || value < -1.7976931348623157e308 {");
        line(builder, "\t\treturn fmt.Sprintf(\"%v\", value)");
        line(builder, "\t}");
        line(builder, "\ttext := strconv.FormatFloat(value, 'f', 6, 64)");
        line(builder, "\tif strings.Contains(text, \".\") {");
        line(builder, "\t\ttext = strings.TrimRight(text, \"0\")");
        line(builder, "\t\ttext = strings.TrimSuffix(text, \".\")");
        line(builder, "\t}");
        line(builder, "\tif text == \"-0\" || text == \"\" {");
        line(builder, "\t\ttext = \"0\"");
        line(builder, "\t}");
        line(builder, "\treturn text");
        line(builder, "}");
        line(builder, "func judgeFmtInt(value int) string { return strconv.Itoa(value) }");
        line(builder, "func judgeFmtInt64(value int64) string { return strconv.FormatInt(value, 10) }");
        line(builder, "func judgeFmtFloat(value float64) string { return judgeCanon(value) }");
        line(builder, "func judgeFmtBool(value bool) string { if value { return \"true\" }; return \"false\" }");
        line(builder, "func judgeFmtString(value string) string { return strings.Join(strings.Fields(value), \"\") }");
        emitGoSliceFormatter(builder, "Int", "[]int", "strconv.Itoa(value)");
        emitGoSliceFormatter(builder, "Int64", "[]int64", "strconv.FormatInt(value, 10)");
        emitGoSliceFormatter(builder, "Float", "[]float64", "judgeCanon(value)");
        emitGoSliceFormatter(builder, "Bool", "[]bool", "judgeFmtBool(value)");
        emitGoSliceFormatter(builder, "String", "[]string", "judgeFmtString(value)");
        blank(builder);
        line(builder, "type judgeOutcome struct {");
        line(builder, "\tpassed  bool");
        line(builder, "\tmessage string");
        line(builder, "}");
        blank(builder);

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            line(builder, "func judgeTest" + (t + 1) + "() (outcome judgeOutcome) {");
            line(builder, "\tdefer func() {");
            line(builder, "\t\tif recovered := recover(); recovered != nil {");
            line(builder, "\t\t\toutcome = judgeOutcome{false, fmt.Sprintf(\"RUNTIME_ERROR|Test Case "
                    + (t + 1) + ": %v\", recovered)}");
            line(builder, "\t\t}");
            line(builder, "\t}()");
            StringBuilder call = new StringBuilder(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(goLiteral(parameters.get(i), value));
            }
            call.append(')');
            line(builder, "\tactual := " + call);
            line(builder, "\tgot := " + goFormatter(returnKind, "actual"));
            line(builder, "\tif got == " + HarnessSpec.quoted(expected) + " {");
            line(builder, "\t\treturn judgeOutcome{true, \"\"}");
            line(builder, "\t}");
            line(builder, "\treturn judgeOutcome{false, fmt.Sprintf(\"WRONG_ANSWER|Test Case " + (t + 1)
                    + " Failed. Expected: %s, Got: %s\", " + HarnessSpec.quoted(expected) + ", got)}");
            line(builder, "}");
            blank(builder);
        }

        line(builder, "func main() {");
        StringBuilder tests = new StringBuilder("[]func() judgeOutcome{");
        for (int t = 0; t < spec.testCases().size(); t++) {
            if (t > 0) {
                tests.append(", ");
            }
            tests.append("judgeTest").append(t + 1);
        }
        tests.append('}');
        line(builder, "\ttests := " + tests);
        line(builder, "\tpassed := 0");
        if (protocol == Protocol.ARENA) {
            line(builder, "\tfirstError := \"\"");
            line(builder, "\thasError := false");
        }
        line(builder, "\tfor _, test := range tests {");
        line(builder, "\t\toutcome := test()");
        line(builder, "\t\tif outcome.passed {");
        line(builder, "\t\t\tpassed++");
        if (protocol == Protocol.ARENA) {
            line(builder, "\t\t} else if !hasError {");
            line(builder, "\t\t\thasError = true");
            line(builder, "\t\t\tfirstError = outcome.message");
        } else {
            line(builder, "\t\t} else {");
            line(builder, "\t\t\tfmt.Println(outcome.message)");
        }
        line(builder, "\t\t}");
        line(builder, "\t}");
        if (protocol == Protocol.ARENA) {
            line(builder, "\tfmt.Printf(\"PROGRESS|%d/%d\\n\", passed, len(tests))");
            line(builder, "\tif hasError {");
            line(builder, "\t\tfmt.Println(firstError)");
            line(builder, "\t} else {");
            line(builder, "\t\tfmt.Println(\"ALL_TESTS_PASSED\")");
            line(builder, "\t}");
        } else {
            line(builder, "\tfmt.Printf(\"ASSESSMENT_RESULT|%d|%d\\n\", passed, len(tests))");
        }
        line(builder, "}");
        return builder.toString();
    }

    private static void emitGoSliceFormatter(StringBuilder builder, String suffix, String sliceType,
                                             String expression) {
        line(builder, "func judgeFmt" + suffix + "Slice(values " + sliceType + ") string {");
        line(builder, "\tparts := make([]string, len(values))");
        line(builder, "\tfor i, value := range values { parts[i] = " + expression + " }");
        line(builder, "\treturn \"[\" + strings.Join(parts, \",\") + \"]\"");
        line(builder, "}");
    }

    private static String goLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT, LONG -> value.toString();
            case DOUBLE -> decimal(value);
            case BOOLEAN -> value.toString();
            case STRING -> HarnessSpec.quoted((String) value);
            case INT_ARRAY -> "[]int{" + joinInts(values(value)) + "}";
            case LONG_ARRAY -> "[]int64{" + joinLongsRaw(values(value)) + "}";
            case DOUBLE_ARRAY -> "[]float64{" + joinDoubles(values(value)) + "}";
            case BOOLEAN_ARRAY -> "[]bool{" + joinBooleans(values(value)) + "}";
            case STRING_ARRAY -> "[]string{" + joinStrings(values(value)) + "}";
        };
    }

    private static String goFormatter(HarnessSpec.Kind kind, String variable) {
        return switch (kind) {
            case INT -> "judgeFmtInt(" + variable + ")";
            case LONG -> "judgeFmtInt64(" + variable + ")";
            case DOUBLE -> "judgeFmtFloat(" + variable + ")";
            case BOOLEAN -> "judgeFmtBool(" + variable + ")";
            case STRING -> "judgeFmtString(" + variable + ")";
            case INT_ARRAY -> "judgeFmtIntSlice(" + variable + ")";
            case LONG_ARRAY -> "judgeFmtInt64Slice(" + variable + ")";
            case DOUBLE_ARRAY -> "judgeFmtFloatSlice(" + variable + ")";
            case BOOLEAN_ARRAY -> "judgeFmtBoolSlice(" + variable + ")";
            case STRING_ARRAY -> "judgeFmtStringSlice(" + variable + ")";
        };
    }

    // =====================================================================
    // Rust
    // =====================================================================

    private static String rust(String userCode, HarnessSpec.Spec spec, Protocol protocol) {
        HarnessSpec.Signature signature = spec.signature();
        List<HarnessSpec.Kind> parameters = signature.parameterKinds();
        HarnessSpec.Kind returnKind = signature.returnKind();
        StringBuilder builder = new StringBuilder(6_000);
        line(builder, "use std::panic;");
        blank(builder);
        builder.append(userCode).append('\n');
        blank(builder);
        line(builder, "struct Solution;");
        blank(builder);
        line(builder, "fn judge_canon(value: f64) -> String {");
        line(builder, "    if value.is_nan() || value.is_infinite() { return format!(\"{}\", value); }");
        line(builder, "    let mut text = format!(\"{:.6}\", value);");
        line(builder, "    if text.contains('.') {");
        line(builder, "        while text.ends_with('0') { text.pop(); }");
        line(builder, "        if text.ends_with('.') { text.pop(); }");
        line(builder, "    }");
        line(builder, "    if text.is_empty() || text == \"-0\" { text = \"0\".to_string(); }");
        line(builder, "    text");
        line(builder, "}");
        line(builder, "fn judge_fmt_i32(value: i32) -> String { value.to_string() }");
        line(builder, "fn judge_fmt_i64(value: i64) -> String { value.to_string() }");
        line(builder, "fn judge_fmt_f64(value: f64) -> String { judge_canon(value) }");
        line(builder, "fn judge_fmt_bool(value: bool) -> String {"
                + " if value { \"true\".to_string() } else { \"false\".to_string() } }");
        line(builder, "fn judge_fmt_string(value: &str) -> String {"
                + " value.chars().filter(|c| !c.is_whitespace()).collect() }");
        emitRustSliceFormatter(builder, "i32", "judge_fmt_i32(*v)");
        emitRustSliceFormatter(builder, "i64", "judge_fmt_i64(*v)");
        emitRustSliceFormatter(builder, "f64", "judge_fmt_f64(*v)");
        emitRustSliceFormatter(builder, "bool", "judge_fmt_bool(*v)");
        line(builder, "fn judge_fmt_vec_string(values: &[String]) -> String {");
        line(builder, "    format!(\"[{}]\", values.iter().map(|v| judge_fmt_string(v))"
                + ".collect::<Vec<_>>().join(\",\"))");
        line(builder, "}");
        line(builder, "fn judge_panic_message(payload: Box<dyn std::any::Any + Send>) -> String {");
        line(builder, "    if let Some(message) = payload.downcast_ref::<&str>() { (*message).to_string() }");
        line(builder, "    else if let Some(message) = payload.downcast_ref::<String>() { message.clone() }");
        line(builder, "    else { \"panic\".to_string() }");
        line(builder, "}");
        blank(builder);
        line(builder, "fn main() {");
        line(builder, "    panic::set_hook(Box::new(|_| {}));");
        line(builder, "    let mut passed: i32 = 0;");
        line(builder, "    let total: i32 = " + spec.testCases().size() + ";");
        if (protocol == Protocol.ARENA) {
            line(builder, "    let mut first_error: Option<String> = None;");
        }

        for (int t = 0; t < spec.testCases().size(); t++) {
            HarnessSpec.TestCase testCase = spec.testCases().get(t);
            String expected = HarnessSpec.expectedFor(returnKind, testCase.expectedOutput());
            StringBuilder call = new StringBuilder("Solution::").append(signature.methodName()).append('(');
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                Object value = HarnessSpec.parseLiteral(testCase.inputs().get(i), parameters.get(i));
                call.append(rustLiteral(parameters.get(i), value));
            }
            call.append(')');
            line(builder, "    // Test case " + (t + 1));
            line(builder, "    match panic::catch_unwind(panic::AssertUnwindSafe(|| {");
            line(builder, "        let actual = " + call + ";");
            line(builder, "        " + rustFormatter(returnKind, "actual"));
            line(builder, "    })) {");
            line(builder, "        Ok(got) => {");
            line(builder, "            if got == " + HarnessSpec.quoted(expected) + " {");
            line(builder, "                passed += 1;");
            if (protocol == Protocol.ARENA) {
                line(builder, "            } else if first_error.is_none() {");
                line(builder, "                first_error = Some(format!(\"WRONG_ANSWER|Test Case "
                        + (t + 1) + " Failed. Expected: {}, Got: {}\", "
                        + HarnessSpec.quoted(expected) + ", got));");
            } else {
                line(builder, "            } else {");
                line(builder, "                println!(\"WRONG_ANSWER|Test Case " + (t + 1)
                        + " Failed. Expected: {}, Got: {}\", " + HarnessSpec.quoted(expected) + ", got);");
            }
            line(builder, "            }");
            line(builder, "        }");
            if (protocol == Protocol.ARENA) {
                line(builder, "        Err(payload) => if first_error.is_none() {");
                line(builder, "            first_error = Some(format!(\"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": {}\", judge_panic_message(payload)));");
                line(builder, "        },");
            } else {
                line(builder, "        Err(payload) => println!(\"RUNTIME_ERROR|Test Case " + (t + 1)
                        + ": {}\", judge_panic_message(payload)),");
            }
            line(builder, "    }");
        }

        if (protocol == Protocol.ARENA) {
            line(builder, "    println!(\"PROGRESS|{}/{}\", passed, total);");
            line(builder, "    match first_error {");
            line(builder, "        Some(message) => println!(\"{}\", message),");
            line(builder, "        None => println!(\"ALL_TESTS_PASSED\"),");
            line(builder, "    }");
        } else {
            line(builder, "    println!(\"ASSESSMENT_RESULT|{}|{}\", passed, total);");
        }
        line(builder, "}");
        return builder.toString();
    }

    private static void emitRustSliceFormatter(StringBuilder builder, String type, String expression) {
        line(builder, "fn judge_fmt_vec_" + type + "(values: &[" + type + "]) -> String {");
        line(builder, "    format!(\"[{}]\", values.iter().map(|v| " + expression + ")"
                + ".collect::<Vec<_>>().join(\",\"))");
        line(builder, "}");
    }

    private static String rustLiteral(HarnessSpec.Kind kind, Object value) {
        return switch (kind) {
            case INT -> value + "i32";
            case LONG -> value + "i64";
            case DOUBLE -> decimal(value) + "f64";
            case BOOLEAN -> value.toString();
            case STRING -> HarnessSpec.quoted((String) value) + ".to_string()";
            case INT_ARRAY -> "vec![" + joinRustInts(values(value), "i32") + "]";
            case LONG_ARRAY -> "vec![" + joinRustInts(values(value), "i64") + "]";
            case DOUBLE_ARRAY -> "vec![" + joinRustDoubles(values(value)) + "]";
            case BOOLEAN_ARRAY -> "vec![" + joinBooleans(values(value)) + "]";
            case STRING_ARRAY -> "vec![" + values(value).stream()
                    .map(item -> HarnessSpec.quoted((String) item) + ".to_string()")
                    .reduce((a, b) -> a + ", " + b).orElse("") + "]";
        };
    }

    /**
     * The Rust closure returns the formatter expression directly. Scalars are
     * passed by value, slices by reference.
     */
    private static String rustFormatter(HarnessSpec.Kind kind, String variable) {
        return switch (kind) {
            case INT -> "judge_fmt_i32(" + variable + ")";
            case LONG -> "judge_fmt_i64(" + variable + ")";
            case DOUBLE -> "judge_fmt_f64(" + variable + ")";
            case BOOLEAN -> "judge_fmt_bool(" + variable + ")";
            case STRING -> "judge_fmt_string(&" + variable + ")";
            case INT_ARRAY -> "judge_fmt_vec_i32(&" + variable + ")";
            case LONG_ARRAY -> "judge_fmt_vec_i64(&" + variable + ")";
            case DOUBLE_ARRAY -> "judge_fmt_vec_f64(&" + variable + ")";
            case BOOLEAN_ARRAY -> "judge_fmt_vec_bool(&" + variable + ")";
            case STRING_ARRAY -> "judge_fmt_vec_string(&" + variable + ")";
        };
    }

    // =====================================================================
    // Starter code
    // =====================================================================

    public static String starterCode(HarnessLanguage language, HarnessSpec.Signature signature) {
        return switch (language) {
            case JAVA -> starterJava(signature);
            case PYTHON -> starterPython(signature);
            case CPP -> starterCpp(signature);
            case C -> starterC(signature);
            case JAVASCRIPT -> starterJavascript(signature);
            case TYPESCRIPT -> starterTypescript(signature);
            case GO -> starterGo(signature);
            case RUST -> starterRust(signature);
        };
    }

    private static String starterJava(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "class Solution {");
        line(builder, "    public " + javaType(signature.returnKind()) + " " + signature.methodName()
                + "(" + javaParameters(signature) + ") {");
        line(builder, "        // Write your solution here");
        line(builder, "        return " + javaDefault(signature.returnKind()) + ";");
        line(builder, "    }");
        line(builder, "}");
        return builder.toString();
    }

    private static String starterPython(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "class Solution:");
        line(builder, "    def " + signature.methodName() + "(self" + pythonParameters(signature) + "):");
        line(builder, "        # Write your solution here");
        line(builder, "        pass");
        return builder.toString();
    }

    private static String starterCpp(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "class Solution {");
        line(builder, "public:");
        line(builder, "    " + cppType(signature.returnKind()) + " " + signature.methodName()
                + "(" + cppParameters(signature) + ") {");
        line(builder, "        // Write your solution here");
        line(builder, "        return " + cppDefault(signature.returnKind()) + ";");
        line(builder, "    }");
        line(builder, "};");
        return builder.toString();
    }

    private static String starterC(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, cType(signature.returnKind()) + " " + signature.methodName()
                + "(" + cParameters(signature) + ") {");
        line(builder, "    // Write your solution here");
        if (signature.returnKind().isArray()) {
            line(builder, "    *returnSize = 0;");
            line(builder, "    return NULL;");
        } else {
            line(builder, "    return " + cDefault(signature.returnKind()) + ";");
        }
        line(builder, "}");
        return builder.toString();
    }

    private static String starterJavascript(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "class Solution {");
        line(builder, "    " + signature.methodName() + "(" + plainParameters(signature) + ") {");
        line(builder, "        // Write your solution here");
        line(builder, "        return " + javascriptDefault(signature.returnKind()) + ";");
        line(builder, "    }");
        line(builder, "}");
        return builder.toString();
    }

    private static String starterTypescript(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "class Solution {");
        line(builder, "    " + signature.methodName() + "(" + typeScriptParameters(signature) + "): "
                + typescriptType(signature.returnKind()) + " {");
        line(builder, "        // Write your solution here");
        line(builder, "        return " + javascriptDefault(signature.returnKind()) + ";");
        line(builder, "    }");
        line(builder, "}");
        return builder.toString();
    }

    private static String starterGo(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "func " + signature.methodName() + "(" + goParameters(signature) + ") "
                + goType(signature.returnKind()) + " {");
        line(builder, "\t// Write your solution here");
        line(builder, "\treturn " + goDefault(signature.returnKind()));
        line(builder, "}");
        return builder.toString();
    }

    private static String starterRust(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        line(builder, "impl Solution {");
        line(builder, "    pub fn " + signature.methodName() + "(" + rustParameters(signature) + ") -> "
                + rustType(signature.returnKind()) + " {");
        line(builder, "        // Write your solution here");
        line(builder, "        " + rustDefault(signature.returnKind()));
        line(builder, "    }");
        line(builder, "}");
        return builder.toString();
    }

    private static String javaParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<String> types = signature.parameterTypes();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(types.get(i)).append(" arg").append(i);
        }
        return builder.toString();
    }

    private static String pythonParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < signature.parameterTypes().size(); i++) {
            builder.append(", arg").append(i);
        }
        return builder.toString();
    }

    private static String plainParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < signature.parameterTypes().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("arg").append(i);
        }
        return builder.toString();
    }

    private static String cppParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(cppType(kinds.get(i))).append(" arg").append(i);
        }
        return builder.toString();
    }

    private static String cParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            HarnessSpec.Kind kind = kinds.get(i);
            if (kind == HarnessSpec.Kind.INT_ARRAY) {
                builder.append("int* arg").append(i).append(", int arg").append(i).append("Size");
            } else {
                builder.append(cType(kind)).append(" arg").append(i);
            }
        }
        if (signature.returnKind().isArray()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("int* returnSize");
        }
        return builder.toString();
    }

    private static String typeScriptParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("arg").append(i).append(": ").append(typescriptType(kinds.get(i)));
        }
        return builder.toString();
    }

    private static String goParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("arg").append(i).append(" ").append(goType(kinds.get(i)));
        }
        return builder.toString();
    }

    private static String rustParameters(HarnessSpec.Signature signature) {
        StringBuilder builder = new StringBuilder();
        List<HarnessSpec.Kind> kinds = signature.parameterKinds();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("arg").append(i).append(": ").append(rustType(kinds.get(i)));
        }
        return builder.toString();
    }

    private static String javaDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INT_ARRAY -> "new int[]{}";
            case LONG_ARRAY -> "new long[]{}";
            case DOUBLE_ARRAY -> "new double[]{}";
            case BOOLEAN_ARRAY -> "new boolean[]{}";
            case STRING_ARRAY -> "new String[]{}";
        };
    }

    private static String cppDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, STRING_ARRAY -> "{}";
        };
    }

    private static String cDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            default -> "NULL";
        };
    }

    private static String javascriptDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG, DOUBLE -> "0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, STRING_ARRAY -> "[]";
        };
    }

    private static String goDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG, DOUBLE -> "0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, STRING_ARRAY -> "nil";
        };
    }

    private static String rustDefault(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "0";
            case LONG -> "0i64";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "String::new()";
            case INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, STRING_ARRAY -> "Vec::new()";
        };
    }

    private static String typescriptType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT, LONG, DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            case STRING -> "string";
            case INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY -> "number[]";
            case BOOLEAN_ARRAY -> "boolean[]";
            case STRING_ARRAY -> "string[]";
        };
    }

    private static String goType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "int";
            case LONG -> "int64";
            case DOUBLE -> "float64";
            case BOOLEAN -> "bool";
            case STRING -> "string";
            case INT_ARRAY -> "[]int";
            case LONG_ARRAY -> "[]int64";
            case DOUBLE_ARRAY -> "[]float64";
            case BOOLEAN_ARRAY -> "[]bool";
            case STRING_ARRAY -> "[]string";
        };
    }

    private static String rustType(HarnessSpec.Kind kind) {
        return switch (kind) {
            case INT -> "i32";
            case LONG -> "i64";
            case DOUBLE -> "f64";
            case BOOLEAN -> "bool";
            case STRING -> "String";
            case INT_ARRAY -> "Vec<i32>";
            case LONG_ARRAY -> "Vec<i64>";
            case DOUBLE_ARRAY -> "Vec<f64>";
            case BOOLEAN_ARRAY -> "Vec<bool>";
            case STRING_ARRAY -> "Vec<String>";
        };
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private static List<Object> values(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected an array literal.");
        }
        @SuppressWarnings("unchecked")
        List<Object> cast = (List<Object>) list;
        return cast;
    }

    private static String decimal(Object value) {
        String text = HarnessSpec.formatDouble((Double) value);
        if (!text.contains(".") && !text.contains("e") && !text.contains("E")) {
            text += ".0";
        }
        return text;
    }

    private static String joinInts(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    /** Longs without a suffix, for languages whose numeric literals are untyped. */
    private static String joinLongsRaw(List<Object> values) {
        return joinInts(values);
    }

    private static String joinLongs(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i)).append('L');
        }
        return builder.toString();
    }

    private static String joinDoubles(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(decimal(values.get(i)));
        }
        return builder.toString();
    }

    private static String joinBooleans(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String joinStrings(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(HarnessSpec.quoted((String) values.get(i)));
        }
        return builder.toString();
    }

    private static String joinRustInts(List<Object> values, String suffix) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i)).append(suffix);
        }
        return builder.toString();
    }

    private static String joinRustDoubles(List<Object> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(decimal(values.get(i))).append("f64");
        }
        return builder.toString();
    }

    private static void line(StringBuilder builder, String text) {
        builder.append(text).append('\n');
    }

    private static void blank(StringBuilder builder) {
        builder.append('\n');
    }
}
