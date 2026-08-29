package com.invoiceocr.config;

import com.invoiceocr.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Backs a {@link ConfigurationSource} with a {@link Properties} instance. */
public final class PropertiesConfigurationSource implements ConfigurationSource {

    private final Properties properties;

    public PropertiesConfigurationSource(Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /** Loads a classpath resource; a missing resource yields an empty source. */
    public static ConfigurationSource fromClasspath(String resource) {
        Objects.requireNonNull(resource, "resource");
        ClassLoader loader = PropertiesConfigurationSource.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return ConfigurationSource.empty();
            }
            return new PropertiesConfigurationSource(read(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read classpath configuration: " + resource, e);
        }
    }

    /** Loads an external file; a missing file yields an empty source. */
    public static ConfigurationSource fromFile(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return ConfigurationSource.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new PropertiesConfigurationSource(read(reader));
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read configuration file: " + file, e);
        }
    }

    private static Properties read(Reader reader) throws IOException {
        Properties loaded = new Properties();
        loaded.load(reader);
        return loaded;
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }
}
