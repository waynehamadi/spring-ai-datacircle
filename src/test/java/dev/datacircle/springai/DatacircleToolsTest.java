package dev.datacircle.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class DatacircleToolsTest {

    private static final String APIS = """
            [{"api": "Employee email", "slug": "email-finder", "plans": 5444, "providers": 300,
              "best": {"provider": "Emelia", "plan": "100,000 credits per month", "score": 76.6, "perThousand": 4.99}},
             {"api": "Company profile", "slug": "linkedin-company", "plans": 1, "providers": 1, "best": null}]
            """;
    private static final String BEST = """
            {"api": "Employee email", "slug": "email-finder", "total": 5444,
             "sheetPage": "https://datacircle.dev/email-finder-api-pricing",
             "plans": [{"provider": "Emelia", "plan": "Pro", "api": "Employee email", "rank": 1, "score": 76.64,
                        "perThousand": 4.99, "per": "call", "notFoundPerThousand": 0, "billing": "monthly",
                        "latencyMs": 812, "page": "https://datacircle.dev/find-email-apis/emelia/pro"}]}
            """;
    private static final String PLANS = """
            {"total": 2, "offset": 1, "plans": [{"provider": "Hunter", "plan": "Starter", "api": "People search",
             "rank": 18, "score": null, "perThousand": null, "page": "https://datacircle.dev/p/hunter"}]}
            """;
    private static final String PROVIDER = """
            {"provider": "Bright Data", "slug": "bright-data", "website": "https://brightdata.com",
             "apis": [{"api": "Employee profile", "slug": "linkedin-profile", "rank": 3,
                       "plans": [{"plan": "Pay as you go", "score": 60, "perThousand": 1.5, "per": "record"},
                                 {"plan": "Growth", "score": 71.25, "perThousand": 1.05, "per": "record"}]},
                      {"api": "SERP", "slug": "serp", "rank": null, "plans": []}]}
            """;

    private static HttpServer server;
    private static final List<String> requests = new ArrayList<>();
    private DatacircleTools tools;

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/", exchange -> {
            String target = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(target);
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String body;
            if (path.equals("/v1/apis")) {
                body = APIS;
            } else if (path.equals("/v1/apis/email-finder/best")) {
                body = BEST;
            } else if (path.equals("/v1/apis/broken/best")) {
                body = "{\"api\": ";
            } else if (path.equals("/v1/plans") && target.contains("query=busy")) {
                status = 429;
                body = "{\"error\": \"too many calls\"}";
            } else if (path.equals("/v1/plans")) {
                body = PLANS;
            } else if (path.equals("/v1/providers/bright-data")) {
                body = PROVIDER;
            } else {
                status = 404;
                body = "{\"error\": \"no API has the slug nope\", \"slugs\": [\"email-finder\"]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @BeforeEach
    void setUp() {
        requests.clear();
        tools = new DatacircleTools(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", Duration.ofSeconds(5));
    }

    @Test
    void listsApisAndFiltersThemByWords() {
        assertEquals(
                "- Employee email (email-finder): 5,444 plans from 300 providers. Best: Emelia, 100,000 credits per "
                        + "month, score 76.6/100, $4.99 per 1,000.\n"
                        + "- Company profile (linkedin-company): 1 plan from 1 provider.\n"
                        + "2 of 2 APIs. Pass a slug to datacircle_best_plans or datacircle_search_plans.",
                tools.listApis(null));
        assertTrue(tools.listApis("EMAIL finder").startsWith("- Employee email (email-finder)"));
        assertTrue(tools.listApis("EMAIL finder").contains("1 of 2 APIs."));
        assertEquals(
                "No API's name or slug holds every word of \"zzz\". Leave the query empty to list 2 APIs.",
                tools.listApis(" zzz "));
    }

    @Test
    void givesTheBestPlans() {
        assertEquals(
                "Employee email (email-finder): the best 1 of 5,444 plans. "
                        + "Sheet: https://datacircle.dev/email-finder-api-pricing\n"
                        + "1. Emelia, Pro: score 76.6/100, $4.99 per 1,000 calls, $0 per 1,000 not found, monthly, "
                        + "median 812 ms. https://datacircle.dev/find-email-apis/emelia/pro",
                tools.bestPlans(" Email-Finder ", 3));
        assertEquals(List.of("/v1/apis/email-finder/best?limit=3"), requests);
    }

    @Test
    void searchesThePlans() {
        assertEquals(
                "Found 2 plans; showing 2 to 2.\n"
                        + "- Hunter, Starter (People search): rank 18, not scored, no listed price. "
                        + "https://datacircle.dev/p/hunter",
                tools.searchPlans("people search", "hunter io", "Price", 5, 1));
        assertEquals(List.of("/v1/plans?sort=price&limit=5&offset=1&api=people+search&query=hunter+io"), requests);
    }

    @Test
    void givesAProvidersRanks() {
        assertEquals(
                "Bright Data (https://brightdata.com) sells 2 APIs. Its rank among each API's providers:\n"
                        + "- Employee profile (linkedin-profile): rank 3, 2 plans. Best: Growth, score 71.3/100, "
                        + "$1.05 per 1,000 records.\n"
                        + "- SERP (serp): not ranked, 0 plans.",
                tools.provider("Bright Data"));
        assertEquals(List.of("/v1/providers/bright-data"), requests);
    }

    @Test
    void saysWhatWentWrong() {
        assertEquals(
                "Error: Datacircle API request failed: HTTP 404. no API has the slug nope. "
                        + "Call datacircle_list_apis for every API's slug.",
                tools.bestPlans("nope", null));
        assertEquals(
                "Error: Datacircle API request failed: HTTP 429. too many calls. The API allows 60 calls a minute "
                        + "from one address. Retry in a minute.",
                tools.searchPlans(null, "busy", null, null, null));
        assertEquals("Error: The Datacircle API returned invalid JSON.", tools.bestPlans("broken", null));
        assertEquals("Error: limit must be between 1 and 50", tools.bestPlans("email-finder", 51));
        assertEquals("Error: sort must be one of [price, provider, score]", tools.searchPlans(null, null, "x", null, null));
        assertEquals("Error: offset must be 0 or more", tools.searchPlans(null, null, null, null, -1));
        assertEquals("Error: api must not be empty", tools.bestPlans(" ", null));
        assertTrue(tools.provider("北京").startsWith("Error: Datacircle writes each provider's name in Latin letters"));
        assertTrue(requests.stream().noneMatch(r -> r.contains("limit=51")));
    }

    @Test
    void saysWhenTheApiIsUnreachable() {
        DatacircleTools offline = new DatacircleTools("http://127.0.0.1:1/v1", Duration.ofSeconds(2));
        assertEquals(
                "Error: The Datacircle API refused the connection. Check the network and the base URL.",
                offline.listApis(null));
    }

    @Test
    void springAiMakesFourToolsWithNamedArguments() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        Map<String, ToolCallback> byName = new java.util.TreeMap<>();
        for (ToolCallback callback : callbacks) {
            byName.put(callback.getToolDefinition().name(), callback);
        }
        assertEquals(
                List.of("datacircle_best_plans", "datacircle_list_apis", "datacircle_provider", "datacircle_search_plans"),
                new ArrayList<>(byName.keySet()));
        String schema = byName.get("datacircle_search_plans").getToolDefinition().inputSchema();
        for (String argument : List.of("\"api\"", "\"query\"", "\"sort\"", "\"limit\"", "\"offset\"")) {
            assertTrue(schema.contains(argument), schema);
        }
        String bestSchema = byName.get("datacircle_best_plans").getToolDefinition().inputSchema();
        assertTrue(bestSchema.replace(" ", "").contains("\"required\":[\"api\"]"), bestSchema);

        String answer = byName.get("datacircle_best_plans").call("{\"api\": \"email-finder\", \"limit\": 3}");
        assertTrue(answer.startsWith("Employee email (email-finder): the best 1"), answer);
        assertTrue(answer.contains("\n1. Emelia"), answer);
        assertTrue(byName.get("datacircle_provider").call("{\"provider\": \"bright-data\"}").startsWith("Bright Data"));
        assertTrue(byName.get("datacircle_list_apis").call("{}").contains("2 of 2 APIs"));
        assertTrue(byName.get("datacircle_search_plans").call("{\"query\": \"hunter\"}").startsWith("Found 2 plans"));
    }

    @Test
    void theProviderOfAnMcpServerGivesTheSameFourTools() {
        ToolCallback[] callbacks =
                MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        assertEquals(4, callbacks.length);
        assertFalse(Arrays.stream(callbacks).anyMatch(c -> c.getToolDefinition().description().isBlank()));
    }
}
