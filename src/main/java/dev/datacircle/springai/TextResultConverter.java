package dev.datacircle.springai;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * Gives the model each Datacircle tool's text as it is, where Spring AI's default would send it as a JSON string.
 */
public final class TextResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "" : result.toString();
    }
}
