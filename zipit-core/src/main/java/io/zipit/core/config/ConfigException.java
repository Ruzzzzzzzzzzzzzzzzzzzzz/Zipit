package io.zipit.core.config;

/** Raised when a Zipit configuration file cannot be parsed or validated. */
public class ConfigException extends Exception {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
