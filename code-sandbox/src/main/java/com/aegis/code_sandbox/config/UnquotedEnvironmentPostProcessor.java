package com.aegis.code_sandbox.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * Removes one layer of quoting from host-provided environment values.
 *
 * <p>Some hosting panels (Pterodactyl/Botkeep eggs in particular) pass every
 * configured value with its quotes attached. The JVM then sees
 * {@code "256"}, {@code "false"} or {@code "https://api.example.com"} instead
 * of the intended value. Spring Boot rejects the quoted numbers and booleans
 * during binding and the service cannot start.</p>
 *
 * <p>This post-processor wraps {@code systemEnvironment} and
 * {@code systemProperties} after Spring Boot has installed its origin-aware,
 * relaxed-name system environment source, and before config data resolves
 * profiles, imports and placeholders. Values that begin and end with one
 * matching pair of single or double quotes are unquoted; everything else is
 * only trimmed. No value is ever logged, so secrets such as
 * {@code ONLINECOMPILER_API_KEY} stay out of the startup output.</p>
 *
 * <p>Ordering matters: Spring Boot's
 * {@code SystemEnvironmentPropertySourceEnvironmentPostProcessor} runs at
 * {@link Ordered#HIGHEST_PRECEDENCE} + 4 and replaces the environment source
 * by name. Running before it would discard this wrapper.
 * {@code ConfigDataEnvironmentPostProcessor} runs at
 * {@link Ordered#HIGHEST_PRECEDENCE} + 10 and must see the unquoted values so
 * placeholders in {@code application.yaml} resolve correctly.</p>
 */
public class UnquotedEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * After Boot's system-environment and SPRING_APPLICATION_JSON processors,
     * before config data processing. See the class Javadoc for the exact
     * ordering constraints.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 6;

    private static final String SYSTEM_ENVIRONMENT = "systemEnvironment";
    private static final String SYSTEM_PROPERTIES = "systemProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        replaceWithUnquotedSource(sources, SYSTEM_ENVIRONMENT);
        replaceWithUnquotedSource(sources, SYSTEM_PROPERTIES);
    }

    private static void replaceWithUnquotedSource(MutablePropertySources sources, String name) {
        PropertySource<?> source = sources.get(name);
        if (source == null || source instanceof UnquotedPropertySource) {
            return;
        }
        sources.replace(name, new UnquotedPropertySource(source));
    }

    /**
     * Trims the value and removes one matching pair of surrounding quotes.
     * Unmatched or embedded quotes are left untouched so real configuration
     * mistakes are not silently rewritten.
     */
    static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Delegating wrapper that preserves relaxed environment-name lookup,
     * enumerability and origin tracking while unquoting String values.
     */
    static final class UnquotedPropertySource extends EnumerablePropertySource<PropertySource<?>>
            implements OriginLookup<String>, PropertySourceInfo {

        private final PropertySource<?> delegate;

        UnquotedPropertySource(PropertySource<?> delegate) {
            super(delegate.getName(), delegate);
            this.delegate = delegate;
        }

        @Override
        public Object getProperty(String name) {
            Object value = delegate.getProperty(name);
            return value instanceof String text ? unquote(text) : value;
        }

        @Override
        public boolean containsProperty(String name) {
            return delegate.containsProperty(name);
        }

        @Override
        public String[] getPropertyNames() {
            if (delegate instanceof EnumerablePropertySource<?> enumerable) {
                return enumerable.getPropertyNames();
            }
            return new String[0];
        }

        @Override
        @SuppressWarnings("unchecked")
        public Origin getOrigin(String name) {
            if (delegate instanceof OriginLookup<?>) {
                return ((OriginLookup<String>) delegate).getOrigin(name);
            }
            return null;
        }

        @Override
        public boolean isImmutable() {
            return delegate instanceof PropertySourceInfo info && info.isImmutable();
        }

        @Override
        public String getPrefix() {
            return delegate instanceof PropertySourceInfo info ? info.getPrefix() : null;
        }
    }
}
