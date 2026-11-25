# Datacircle tools for Spring AI

Find the best GTM APIs. We benchmark all the GTM APIs and we put the results on our website.

Our website: https://datacircle.dev

This library gives [Spring AI](https://github.com/spring-projects/spring-ai) four tools that call Datacircle's open API, https://api.datacircle.dev/v1. You pass them to a `ChatClient`, or serve them from a Spring AI MCP server beside your app's own tools. You need no key and no account.

| Tool | What the tool gives | Call |
| --- | --- | --- |
| `datacircle_list_apis` | Every GTM API on Datacircle, with the API's slug, number of plans, number of providers and best plan | `GET /apis` |
| `datacircle_best_plans` | One API's plans, sorted by score out of 100, best first. Each plan comes with a rank and a price per 1,000 calls | `GET /apis/{slug}/best` |
| `datacircle_search_plans` | Plans that match a provider or plan name, on all APIs or on one API. You can sort them by score, provider or price | `GET /plans` |
| `datacircle_provider` | The APIs a provider sells, and the provider's rank on each API | `GET /providers/{slug}` |

## Install

The library is on [JitPack](https://jitpack.io/#waynehamadi/spring-ai-datacircle). With Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.waynehamadi</groupId>
    <artifactId>spring-ai-datacircle</artifactId>
    <version>0.1.0</version>
</dependency>
```

With Gradle:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.waynehamadi:spring-ai-datacircle:0.1.0")
}
```

The library uses the Spring AI your app already has, 1.1 or 2.x, on Java 17 or later. It adds no other dependency.

## Use the tools with ChatClient

```java
import dev.datacircle.springai.DatacircleTools;

String answer = chatClient.prompt("Who are the 3 best providers for the email-finder API?")
        .tools(new DatacircleTools())
        .call()
        .content();
```

You can also set the tools once with `ChatClient.builder(chatModel).defaultTools(new DatacircleTools())`.

## Serve the tools from a Spring AI MCP server

Add a Spring AI MCP server starter, such as `spring-ai-starter-mcp-server-webmvc`, and this bean:

```java
import dev.datacircle.springai.DatacircleTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

@Bean
ToolCallbackProvider datacircleTools() {
    return MethodToolCallbackProvider.builder().toolObjects(new DatacircleTools()).build();
}
```

The server lists the four tools beside your app's own.

## What the tools answer

Each tool answers in short text, one API or plan a line, with each plan's page on datacircle.dev. For example, `datacircle_best_plans` with `{"api": "email-finder", "limit": 3}` answers:

```text
Employee email (email-finder): the best 3 of 5,444 plans. Sheet: https://datacircle.dev/email-finder-api-pricing
1. Emelia, 100,000 credits per month: score 76.6/100, $4.99 per 1,000 calls, $0 per 1,000 not found, monthly. https://datacircle.dev/find-email-apis/emelia/19-monthly
2. ...
```

When a call fails, the tool answers with a line that starts with `Error:` and says what to do, for example when a slug does not exist. It does not throw.

## Options

`new DatacircleTools(baseUrl, timeout)` sets another base URL or another timeout. The defaults are `https://api.datacircle.dev/v1` and 30 seconds.

The API allows 60 calls a minute from one address. Its spec is at https://api.datacircle.dev/v1/openapi.json.

## Tests

```bash
mvn test                      # offline, on a local server
DATACIRCLE_LIVE=1 mvn test    # also calls each tool once on the live API
```

## License

MIT
