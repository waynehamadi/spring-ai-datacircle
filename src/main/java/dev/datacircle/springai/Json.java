package dev.datacircle.springai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, so that the tools run on the Jackson of Spring AI 1.1 and of 2.x alike.
 *
 * <p>An object is a {@link Map}, an array a {@link List}, a number a {@link BigDecimal}, then {@link String},
 * {@link Boolean} and {@code null}.
 */
final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json json = new Json(text);
        json.skipSpace();
        Object value = json.value();
        json.skipSpace();
        if (json.at != text.length()) {
            throw json.error("text after the value");
        }
        return value;
    }

    private Object value() {
        if (at >= text.length()) {
            throw error("the end of the text");
        }
        char c = text.charAt(at);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("'" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> object = new LinkedHashMap<>();
        at++;
        skipSpace();
        if (next('}')) {
            return object;
        }
        while (true) {
            skipSpace();
            if (at >= text.length() || text.charAt(at) != '"') {
                throw error("no key");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            object.put(key, value());
            skipSpace();
            if (next(',')) {
                continue;
            }
            expect('}');
            return object;
        }
    }

    private List<Object> array() {
        List<Object> array = new ArrayList<>();
        at++;
        skipSpace();
        if (next(']')) {
            return array;
        }
        while (true) {
            skipSpace();
            array.add(value());
            skipSpace();
            if (next(',')) {
                continue;
            }
            expect(']');
            return array;
        }
    }

    private String string() {
        at++;
        StringBuilder out = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("a short \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("a bad \\u escape");
                    }
                    at += 4;
                }
                default -> throw error("a bad escape");
            }
        }
        throw error("a string with no end");
    }

    private BigDecimal number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        try {
            return new BigDecimal(text.substring(start, at));
        } catch (NumberFormatException e) {
            throw error("a bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("'" + text.charAt(at) + "'");
        }
        at += word.length();
        return value;
    }

    private boolean next(char c) {
        if (at < text.length() && text.charAt(at) == c) {
            at++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!next(c)) {
            throw error("no '" + c + "'");
        }
    }

    private void skipSpace() {
        while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Invalid JSON: " + what + " at character " + at);
    }
}
