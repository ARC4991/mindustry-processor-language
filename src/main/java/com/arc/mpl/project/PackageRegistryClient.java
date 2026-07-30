package com.arc.mpl.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Reads the small JSON package index published by the project's IO site. */
public final class PackageRegistryClient {
    public static final String DEFAULT_INDEX =
        "https://arc4991.github.io/mindustry-processor-language-io/registry/index.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client;

    public PackageRegistryClient() {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(TIMEOUT);
        String proxy = Optional.ofNullable(System.getenv("MPL_HTTP_PROXY"))
            .or(() -> Optional.ofNullable(System.getenv("HTTPS_PROXY")))
            .orElse("");
        if (!proxy.isBlank()) {
            try {
                URI uri = URI.create(proxy);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to the JVM's normal network configuration.
            }
        }
        client = builder.build();
    }

    public List<Entry> search(String query) throws IOException {
        URI index = URI.create(System.getenv().getOrDefault("MPL_PACKAGE_INDEX_URL", DEFAULT_INDEX));
        HttpRequest request = HttpRequest.newBuilder(index).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("包索引请求失败，HTTP " + response.statusCode());
            }
            JsonNode root = JSON.readTree(response.body());
            JsonNode packages = root.path("packages");
            if (!packages.isObject()) throw new IOException("包索引的 packages 必须是对象");
            String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
            List<Entry> result = new ArrayList<>();
            packages.properties().forEach(property -> {
                JsonNode value = property.getValue();
                if (!value.isObject()) return;
                Entry entry = new Entry(property.getKey(), text(value, "version", "0.0.0"),
                    text(value, "source", ""), text(value, "description", ""));
                if (needle.isBlank() || entry.name().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.description().toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
            });
            return result.stream().sorted(Comparator.comparing(Entry::name)).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("包索引请求被中断", exception);
        }
    }

    public Entry require(String name) throws IOException {
        return search(name).stream().filter(entry -> entry.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("包索引中不存在：" + name));
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : fallback;
    }

    public record Entry(String name, String version, String source, String description) {
        public Entry {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("包索引名称不能为空");
            if (source == null || source.isBlank()) throw new IllegalArgumentException("包索引来源不能为空：" + name);
        }
    }
}
