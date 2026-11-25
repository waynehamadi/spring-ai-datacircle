package dev.datacircle.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void readsEveryKindOfValue() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", List.of(new BigDecimal("1"), new BigDecimal("-2.5e3"), Boolean.TRUE, Boolean.FALSE));
        expected.put("b", null);
        expected.put("c", "x\"\\/\b\f\n\r\té€");
        expected.put("d", Map.of());
        expected.put("e", List.of());
        assertEquals(
                expected,
                Json.parse(" {\"a\": [1, -2.5e3, true, false], \"b\": null, "
                        + "\"c\": \"x\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e9€\", \"d\": {}, \"e\": [ ]} "));
        assertEquals(Arrays.asList("x", null), Json.parse("[\"x\",null]"));
    }

    @Test
    void refusesBrokenJson() {
        for (String broken : List.of("", "{", "[1,", "{\"a\" 1}", "\"abc", "nul", "{\"a\": 1} x", "[1 2]", "\"\\u12\"",
                "{a: 1}", "-")) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(broken), broken);
        }
    }
}
