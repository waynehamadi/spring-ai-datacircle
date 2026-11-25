package dev.datacircle.springai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/** Each tool once on the live API, through Spring AI's own tool callbacks. Run with DATACIRCLE_LIVE=1. */
@EnabledIfEnvironmentVariable(named = "DATACIRCLE_LIVE", matches = "1")
class DatacircleToolsLiveTest {

    private final Map<String, ToolCallback> tools = new HashMap<>();

    DatacircleToolsLiveTest() {
        for (ToolCallback callback : ToolCallbacks.from(new DatacircleTools())) {
            tools.put(callback.getToolDefinition().name(), callback);
        }
    }

    @Test
    void listsTheApis() {
        String answer = tools.get("datacircle_list_apis").call("{\"query\": \"email\"}");
        System.out.println(answer);
        assertTrue(answer.contains("(email-finder): "), answer);
        assertTrue(answer.contains(" APIs. Pass a slug to datacircle_best_plans"), answer);
    }

    @Test
    void givesTheBestPlans() {
        String answer = tools.get("datacircle_best_plans").call("{\"api\": \"email-finder\", \"limit\": 3}");
        System.out.println(answer);
        assertTrue(answer.startsWith("Employee email (email-finder): the best 3 of "), answer);
        assertTrue(answer.contains("\n1. ") && answer.contains("\n3. "), answer);
    }

    @Test
    void searchesThePlans() {
        String answer = tools.get("datacircle_search_plans").call("{\"query\": \"hunter\", \"sort\": \"price\", \"limit\": 3}");
        System.out.println(answer);
        assertTrue(answer.startsWith("Found ") && answer.contains("; showing 1 to 3."), answer);
        assertTrue(answer.contains("- Hunter, "), answer);
    }

    @Test
    void givesAProvidersRanks() {
        String answer = tools.get("datacircle_provider").call("{\"provider\": \"Hunter\"}");
        System.out.println(answer);
        assertTrue(answer.startsWith("Hunter (https://hunter.io) sells "), answer);
        assertTrue(answer.contains(": rank "), answer);
    }
}
