package io.zipit.licenseserver.auth;

import io.zipit.licenseserver.Json;

/**
 * Tiny fluent JSON emitter for the auth API responses.
 *
 * <p>The server only ever returns flat objects with (nested) arrays of flat
 * objects, so a full JSON library is unnecessary. Values are appended in
 * insertion order and string values are escaped by {@link Json#quote(String)}.</p>
 */
public final class JsonOut {

    private final char opening;
    private final StringBuilder out = new StringBuilder(160);
    private boolean hasEntries;
    private boolean closed;

    private JsonOut(char opening) {
        this.opening = opening;
        this.out.append(opening);
    }

    /** Starts a JSON object ({@code {}}). */
    public static JsonOut object() {
        return new JsonOut('{');
    }

    /** Starts a JSON array ({@code []}). */
    public static JsonOut array() {
        return new JsonOut('[');
    }

    /** Adds a named field to a JSON object. */
    public JsonOut add(String name, String value) {
        return raw(name, value == null ? "null" : Json.quote(value));
    }

    public JsonOut add(String name, long value) {
        return raw(name, Long.toString(value));
    }

    public JsonOut add(String name, int value) {
        return raw(name, Integer.toString(value));
    }

    public JsonOut add(String name, boolean value) {
        return raw(name, Json.bool(value));
    }

    /** Adds a pre-serialised JSON value (used for nested objects/arrays). */
    public JsonOut raw(String name, String rawJson) {
        if (opening != '{') {
            throw new IllegalStateException("Not a JSON object");
        }
        separator();
        out.append(Json.quote(name)).append(':').append(rawJson == null ? "null" : rawJson);
        return this;
    }

    /** Appends a pre-serialised JSON value to a JSON array. */
    public JsonOut item(String rawJson) {
        if (opening != '[') {
            throw new IllegalStateException("Not a JSON array");
        }
        separator();
        out.append(rawJson == null ? "null" : rawJson);
        return this;
    }

    public JsonOut item(JsonOut other) {
        return item(other.toString());
    }

    public JsonOut item(String value, boolean quote) {
        return item(quote ? Json.quote(value) : value);
    }

    private void separator() {
        if (closed) {
            throw new IllegalStateException("JSON value already closed");
        }
        if (hasEntries) {
            out.append(',');
        }
        hasEntries = true;
    }

    @Override
    public String toString() {
        if (!closed) {
            out.append(opening == '{' ? '}' : ']');
            closed = true;
        }
        return out.toString();
    }
}
