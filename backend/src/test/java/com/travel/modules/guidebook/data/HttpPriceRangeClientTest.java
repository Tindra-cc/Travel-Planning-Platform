package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.support.TestFixtures;

class HttpPriceRangeClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesConfiguredResponse() throws IOException {
        String body = "{\"min\":800,\"max\":1200,\"currency\":\"CNY\",\"freshness\":\"2026-10-20\"}";
        String url = startServer(200, body);
        HttpPriceRangeClient client = new HttpPriceRangeClient(new ObjectMapper(), properties(url));

        Optional<DataBlock> block = client.fetch("flight_price", "机票", url, TestFixtures.intent());

        assertThat(block).isPresent();
        assertThat(block.get().summary()).contains("机票参考价", "800", "1200", "CNY");
        assertThat(block.get().data()).containsEntry("destination", "成都");
    }

    @Test
    void missingRequiredFieldsReturnsEmpty() throws IOException {
        String url = startServer(200, "{\"min\":800}");
        HttpPriceRangeClient client = new HttpPriceRangeClient(new ObjectMapper(), properties(url));

        assertThat(client.fetch("flight_price", "机票", url, TestFixtures.intent())).isEmpty();
    }

    @Test
    void nonSuccessStatusReturnsEmpty() throws IOException {
        String url = startServer(500, "boom");
        HttpPriceRangeClient client = new HttpPriceRangeClient(new ObjectMapper(), properties(url));

        assertThat(client.fetch("flight_price", "机票", url, TestFixtures.intent())).isEmpty();
    }

    @Test
    void unconfiguredUrlReturnsEmptyWithoutRequest() {
        HttpPriceRangeClient client = new HttpPriceRangeClient(new ObjectMapper(), properties(null));

        assertThat(client.fetch("flight_price", "机票", null, TestFixtures.intent())).isEmpty();
    }

    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/price", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/price";
    }

    private TravelDataProperties properties(String url) {
        TravelDataProperties properties = new TravelDataProperties();
        properties.setFlightPriceUrl(url);
        properties.setTimeoutSeconds(2);
        return properties;
    }
}
