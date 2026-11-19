package com.aegis.code_sandbox.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Accepts boolean configuration values that were wrapped in quotes.
 *
 * <p>Hosting panels (Pterodactyl/Botkeep eggs, some Compose versions, Windows
 * shells) can hand the JVM the six-character string {@code "false"} — quote
 * characters included — instead of the boolean {@code false}. Spring's default
 * converter rejects that and aborts startup with
 * {@code Invalid boolean value '"false"'}, even though the configuration is
 * semantically correct.</p>
 *
 * <p>This converter strips one layer of matching single or double quotes, then
 * applies the standard Spring spellings ({@code true/false}, {@code yes/no},
 * {@code on/off}, {@code 1/0}). A genuinely invalid value still fails startup
 * with the same {@code Invalid boolean value} error, and unmatched quotes are
 * left alone so real typos are not hidden.</p>
 *
 * <p>{@link UnquotedEnvironmentPostProcessor} normally removes host-injected
 * quotes from every environment value before binding. This converter remains a
 * cheap safety net for boolean values that arrive by another route.</p>
 */
@Component
@ConfigurationPropertiesBinding
public class LenientBooleanConverter implements Converter<String, Boolean> {

    private static final Set<String> TRUE_VALUES = Set.of("true", "on", "yes", "1");
    private static final Set<String> FALSE_VALUES = Set.of("false", "off", "no", "0");

    @Override
    public Boolean convert(String source) {
        if (source == null) {
            return null;
        }
        String value = unquote(source);
        if (value.isEmpty()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return Boolean.FALSE;
        }
        // Match Spring's message so operators still get a familiar startup error.
        throw new IllegalArgumentException("Invalid boolean value '" + source + "'");
    }

    /**
     * Trims whitespace and removes one pair of matching surrounding quotes.
     * Returns {@code null} only when the input is {@code null}, which lets the
     * binder treat a missing value as unset.
     */
    static String unquote(String source) {
        if (source == null) {
            return null;
        }
        String value = source.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }
}
