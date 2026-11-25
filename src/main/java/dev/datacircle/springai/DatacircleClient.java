package dev.datacircle.springai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLException;

/**
 * Low-level client for the open Datacircle API at {@code https://api.datacircle.dev/v1}.
 *
 * <p>The API needs no key and no account. It allows 60 calls a minute from one address.
 */
final class DatacircleClient {

    static final String DEFAULT_BASE_URL = "https://api.datacircle.dev/v1";
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final Set<String> SORTS = Set.of("score", "provider", "price");

    private static final String USER_AGENT = "spring-ai-datacircle/0.1.0";
    private static final int MAX_ERROR_LENGTH = 300;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    DatacircleClient(String baseUrl, Duration timeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** {@code GET /apis}: every API on the sheet, in its order, an array. */
    Object listApis() {
        return get("/apis");
    }

    /** {@code GET /apis/{slug}/best}: one API's plans, best first by score. */
    Object bestPlans(String slug, Integer limit) {
        return get("/apis/" + encodePath(normalizeSlug(slug, "api")) + "/best?limit=" + normalizeLimit(limit));
    }

    /** {@code GET /plans}: the plans of every API, or of one, by words in the provider's or plan's name. */
    Object searchPlans(String api, String query, String sort, Integer limit, Integer offset) {
        StringBuilder path = new StringBuilder("/plans?sort=")
                .append(normalizeSort(sort))
                .append("&limit=")
                .append(normalizeLimit(limit))
                .append("&offset=")
                .append(normalizeOffset(offset));
        appendOptional(path, "api", api);
        appendOptional(path, "query", query);
        return get(path.toString());
    }

    /** {@code GET /providers/{slug}}: one provider's plans and rank on each API it sells. */
    Object provider(String slug) {
        return get("/providers/" + encodePath(providerSlug(slug)));
    }

    /** Turns a provider's name into its slug: accents dropped, lowercase letters and digits, the rest a hyphen. */
    static String providerSlug(String name) {
        String slug = Normalizer.normalize(ensureNotBlank(name, "provider"), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Datacircle writes each provider's name in Latin letters, so \""
                    + name.trim() + "\" finds none. Pass the name in Latin letters or its website's domain, "
                    + "like ihuyi for ihuyi.com, or find the provider with datacircle_search_plans.");
        }
        return slug;
    }

    private Object get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatacircleException("The Datacircle API request was interrupted.", e);
        } catch (IOException | RuntimeException e) {
            throw new DatacircleException(transportErrorMessage(e), e);
        }
        int status = response.statusCode();
        String body = response.body();
        if (status < 200 || status > 299) {
            throw new DatacircleException(status, httpErrorMessage(status, body), null);
        }
        if (body == null || body.isBlank()) {
            throw new DatacircleException("The Datacircle API returned an empty response.");
        }
        try {
            return Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new DatacircleException("The Datacircle API returned invalid JSON.", e);
        }
    }

    /** Says why no answer came: a timeout, an unknown host, a TLS failure, a refused or a lost connection. */
    private String transportErrorMessage(Throwable error) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = error; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        if (causes.stream().anyMatch(HttpTimeoutException.class::isInstance)) {
            return "The Datacircle API did not answer within " + timeout.toSeconds() + " s. Retry in a moment.";
        }
        if (causes.stream()
                .anyMatch(c -> c instanceof UnknownHostException || c instanceof UnresolvedAddressException)) {
            return "The Datacircle API's host " + URI.create(baseUrl).getHost()
                    + " could not be found. Check the network and the base URL.";
        }
        if (causes.stream().anyMatch(SSLException.class::isInstance)) {
            return "The secure connection to the Datacircle API failed (TLS). Check the network and the base URL.";
        }
        if (causes.stream().anyMatch(ConnectException.class::isInstance)) {
            return "The Datacircle API refused the connection. Check the network and the base URL.";
        }
        if (causes.stream().anyMatch(IOException.class::isInstance)) {
            return "The connection to the Datacircle API was lost before it answered. Retry in a moment.";
        }
        return "The Datacircle API request failed: " + error.getClass().getSimpleName() + ".";
    }

    private static String httpErrorMessage(int statusCode, String body) {
        String reason = errorSentence(body);
        if (reason == null) {
            reason = switch (statusCode) {
                case 400 -> "Invalid request. Check the tool arguments.";
                case 404 -> "Not found.";
                case 429 -> "The API allows 60 calls a minute from one address. Retry in a minute.";
                default -> "Request could not be completed.";
            };
        } else if (statusCode == 429) {
            reason = reason + " The API allows 60 calls a minute from one address. Retry in a minute.";
        }
        return "Datacircle API request failed: HTTP " + statusCode + ". " + reason;
    }

    /** The API's own one-line reason, from its {@code {"error": "..."}} body, or {@code null}. */
    private static String errorSentence(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Object parsed;
        try {
            parsed = Json.parse(body);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("error") instanceof String error) || error.isBlank()) {
            return null;
        }
        String sentence = error.trim();
        if (sentence.length() > MAX_ERROR_LENGTH) {
            sentence = sentence.substring(0, MAX_ERROR_LENGTH - 3) + "...";
        }
        return sentence.endsWith(".") ? sentence : sentence + ".";
    }

    private static void appendOptional(StringBuilder path, String name, String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=').append(encode(value.trim()));
        }
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    private static int normalizeOffset(Integer offset) {
        int value = offset == null ? 0 : offset;
        if (value < 0) {
            throw new IllegalArgumentException("offset must be 0 or more");
        }
        return value;
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "score";
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        if (!SORTS.contains(normalized)) {
            throw new IllegalArgumentException("sort must be one of " + SORTS.stream().sorted().toList());
        }
        return normalized;
    }

    private static String normalizeSlug(String slug, String name) {
        return ensureNotBlank(slug, name).trim().toLowerCase(Locale.ROOT);
    }

    private static String ensureNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URL", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must include a host");
        }
        return value;
    }
}
