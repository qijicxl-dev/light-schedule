package com.lightschedule.integration.kingdee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.config.KingdeeProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpKingdeeWritebackClientTest {

    @Test
    void shouldReadExternalRequestIdFromWritebackResponseBodyWhenHeaderIsMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"requestId\":\"REQ-12\",\"message\":\"submitted\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-12");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIgnoreBlankHeaderRequestIdAndUseBodyRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", " ");
            respond(exchange, 200, "{\"requestId\":\"REQ-12B\",\"message\":\"submitted\"}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-12B");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenSuccessfulWritebackResponseHasRequestIdButNoMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"requestId\":\"REQ-12C\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-12C");
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallBackToTopLevelMessageWhenNestedSuccessPayloadMessageIsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"result\":{\"requestId\":\"REQ-12D\",\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-12D");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallBackToTopLevelAliasMessageWhenNestedSuccessPayloadMessageIsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"requestId\":\"REQ-12E\",\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-12E");
            assertThat(result.message()).isEqualTo("accepted without errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageWhenWrapperMessagesAreBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"   \",\"result\":{\"requestId\":\"REQ-77\",\"message\":\"   \"},\"data\":{\"status\":\"ok\",\"success\":true,\"result\":{\"requestId\":\"REQ-77\",\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-77");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldQueryConfiguredWritebackStatusPathAndMapCompletedSuccess() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, "writeback completed");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9");

            assertThat(requestPath.get()).isEqualTo("/k3cloud/schedule/writeback/status");
            assertThat(requestQuery.get()).isEqualTo("requestId=REQ-9");
            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseCompletedSuccessFromJsonStatusResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseWhitespacePaddedCompletedStatusResponseAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\" completed \",\"success\":true,\"message\":\"writeback completed\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9W");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatCompletedStatusWithSuccessFalseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"completed\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageForCompletedFailureWhenMessageIsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"completed\",\"success\":false,\"message\":\"   \"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9B2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatCompletedSuccessStatusWithBlankMessageAsBlankStatusMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"completed\",\"success\":true,\"message\":\"   \"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9BW");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageForNestedCompletedFailureUnderSuccessWrapperWhenMessageIsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"   \",\"result\":{\"status\":\"completed\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9BX");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseSucceededStatusResponseAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"succeeded\",\"message\":\"writeback completed\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenRootSuccessWrapperContainsAcceptedDataFailureWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"   \",\"data\":{\"status\":\"accepted\",\"message\":\"   \",\"result\":{\"status\":\"completed\",\"success\":false,\"message\":\"   \"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9BXB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageForNestedCompletedFailureUnderAcceptedWrapperWhenMessageIsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"   \",\"result\":{\"status\":\"completed\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9BXA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageForExplicitRootFailureWhenNestedAcceptedPayloadExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":false,\"message\":\"   \",\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9EF1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatSucceededStatusWithExplicitSuccessFalseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"succeeded\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9A2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatSuccessStatusWithExplicitSuccessTrueAsOpenWritebackStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"success\",\"success\":true,\"message\":\"processing\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9S");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatSucceededStatusWithSuccessFalseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"succeeded\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9A1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonDeniedStatusAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"denied\",\"message\":\"denied\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-9A3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("denied");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepStatusOpenWhenResponseBodyShowsProcessing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "processing"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextSucceededResponseToCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback succeeded"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback succeeded");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextSuccessResponseToCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback success"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback success");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatAcceptedStatusWrapperWithNestedFailedResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatAcceptedStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9S");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenAcceptedStatusWrapperContainsNestedSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9SN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatSubmittedStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"submitted\",\"message\":\"submitted\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9T");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenSubmittedStatusWrapperContainsNestedSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"submitted\",\"message\":\"submitted\",\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9TN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"submitted\",\"message\":\"submitted\",\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRequestAcceptedStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9U");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedResultOverNestedSubmittedAliasUnderRequestAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"submitted\",\"message\":\"submitted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9UC");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedResultOverNestedSubmittedAliasUnderAcceptedWithoutErrorsWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"data\":{\"result\":{\"status\":\"submitted\",\"message\":\"submitted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9VE");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedResultOverNestedSubmittedAliasUnderWithNoErrorsWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"with no errors\",\"message\":\"with no errors\",\"data\":{\"result\":{\"status\":\"submitted\",\"message\":\"submitted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9WE");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedResultOverNestedSubmittedAliasUnderWithoutErrorWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"without error\",\"message\":\"without error\",\"data\":{\"result\":{\"status\":\"submitted\",\"message\":\"submitted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9XE");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRequestAcceptedStatusWrapperContainsNestedSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9UN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"request accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenRequestAcceptedStatusWrapperContainsNestedSuccessFalseResultWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9UB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatAcceptedWithoutErrorsStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9V");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenAcceptedWithoutErrorsStatusWrapperContainsNestedSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9VN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatWithNoErrorsStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"with no errors\",\"message\":\"with no errors\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9W");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenWithNoErrorsStatusWrapperContainsNestedSuccessFalseResultWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"with no errors\",\"message\":\"with no errors\",\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9WB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureMessageOverTopLevelResultMessageWhenAcceptedWithoutErrorsDataWrapperContainsSparseFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9VQ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureMessageOverTopLevelResultMessageWhenWithNoErrorsDataWrapperContainsSparseFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"with no errors\",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9WQ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureMessageOverTopLevelResultMessageWhenWithoutErrorDataWrapperContainsSparseFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"without error\",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9XQ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatWithoutErrorStatusWrapperWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"without error\",\"message\":\"without error\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A9X");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepJsonQueuedStatusOpenEvenWhenSuccessFieldIsTrue() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"queued\",\"success\":true,\"message\":\"processing\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepSuccessTrueWithoutExplicitBusinessStatusOpen() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"accepted\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10B1");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepPlainJsonSuccessStatusWithoutBusinessPayloadOpen() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10B2");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepPlainJsonOkStatusWithoutBusinessPayloadOpen() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"ok\",\"message\":\"accepted\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10B3");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepNestedProcessingStatusOpenUnderGenericSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepNestedProcessingStatusOpenUnderWhitespacePaddedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\" success \",\"message\":\"accepted\",\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10CW");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepNestedProcessingStatusOpenUnderRequestAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10CR");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedSucceededStatusWithSuccessFalseUnderSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"result\":{\"status\":\"succeeded\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSuccessTrueWithNestedAcceptedAliasAndTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultMessageWhenDataSuccessTrueWrapsAcceptedAliasFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSM");

            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenDataSuccessTrueWrapsAcceptedAliasFailureWithBlankTopLevelMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueWrapsAcceptedAliasFailureWithoutTopLevelMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasFailureLacksWrapperMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSNM");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelFailedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"failed\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSF0");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"failed\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelErrorWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"error\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSER");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"error\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTerminalFailedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal-failed\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal-failed\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTerminalFailedUnderscoreWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal_failed\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTFU");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal_failed\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTerminalFailedCamelWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminalFailed\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTFC");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminalFailed\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTerminalFailedSpacedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal failed\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTFS");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"terminal failed\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelUnsuccessfulWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"unsuccessful\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSU");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"unsuccessful\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelNotSuccessfulWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"not successful\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSNS");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"not successful\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelDeclinedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"declined\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSD");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"declined\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelDeniedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"denied\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSDN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"denied\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelCancelledWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"cancelled\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSC");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"cancelled\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelCanceledWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"canceled\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSCA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"canceled\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelAbortedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"aborted\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSAB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"aborted\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTimeoutWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"timeout\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTO");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"timeout\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelTimedOutWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"timed out\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSTOD");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"timed out\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelForbiddenWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"forbidden\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"forbidden\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelBlockedWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"blocked\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"blocked\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelInvalidWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"invalid\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSI");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"invalid\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSuccessTrueAcceptedAliasWrapsTopLevelExpiredWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"expired\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DSE");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"expired\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSuccessTrueWithNestedSucceededStatusAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1S");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRootSuccessWrapperWithAcceptedDataResultAndTopLevelSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1SA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRootSuccessWrapperWithTopLevelSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1SR");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedFailedMessageUnderWhitespacePaddedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\" success \",\"message\":\"accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1W");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedCompletedStatusWithSuccessFalseUnderSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"result\":{\"status\":\"completed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedCompletedStatusWithSuccessFalseUnderStatusSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"data\":{\"result\":{\"status\":\"completed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedCompletedFailureUnderSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedCompletedFailureUnderRootSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedSucceededStatusWithSuccessFalseUnderRootSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"accepted\",\"data\":{\"result\":{\"status\":\"succeeded\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C4");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedSucceededStatusWithSuccessFalseUnderCompletedSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"accepted\",\"data\":{\"result\":{\"status\":\"succeeded\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C5");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverTopLevelResultMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\"},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultMessageOverTopLevelResultMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AG");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultMessageOverDataMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"generic failure\",\"result\":{\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AH");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultMessageWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AI");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultMessageWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AJ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferRootFailureMessageWhenRootSuccessFalseWrapsNestedSuccessPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":false,\"message\":\"validation error\",\"data\":{\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AJ2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultMessageWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AK");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedSuccessMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AL");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedSuccessMessageWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AM");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedSuccessMessageWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AN");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedSuccessMessageWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AO");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedAcceptedAliasMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11APA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedStatusSuccessMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AP");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedStatusSuccessMessageWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AQ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedStatusSuccessMessageWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AR");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedStatusSuccessMessageWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AS");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedCompletedSuccessMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AT");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedCompletedSuccessMessageWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AU");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedCompletedSuccessMessageWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AV");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedCompletedSuccessMessageWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AW");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedOpenSuccessMessageWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AX");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedOpenSuccessMessageWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AY");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedOpenSuccessMessageWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AZ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureMessageOverNestedOpenSuccessMessageWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapRejectedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "Kingdee rejected payload"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Kingdee rejected payload");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextFailedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "validation failed"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation failed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextUnsuccessfulResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback unsuccessful"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("writeback unsuccessful");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextNotSuccessfulResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback not successful"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("writeback not successful");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextNotCompletedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback not completed"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AC");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("writeback not completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextNotSucceededResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback not succeeded"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11AD");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("writeback not succeeded");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextErrorResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "validation error"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextDeniedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "denied"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BA");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("denied");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextCancelledResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "cancelled"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BB");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("cancelled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextCanceledResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "canceled"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BC");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("canceled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextAbortedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "aborted"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BD");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("aborted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextTimeoutResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "timeout"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BE");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("timeout");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextForbiddenResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "forbidden"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("forbidden");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextBlockedResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "blocked"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BG");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("blocked");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextInvalidResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "invalid"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BH");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("invalid");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextExpiredResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "expired"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BI");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("expired");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextTimedOutResponseToCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "timed out"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11BJ");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("timed out");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextCompletedWithoutErrorsResponseToCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback completed without errors"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11E");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed without errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextCompletedWithNoErrorsResponseToCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback completed with no errors"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11F");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed with no errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapPlainTextCompletedWithoutErrorResponseToCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "writeback completed without error"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11G");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed without error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseFailedJsonStatusAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"failed\",\"success\":false,\"message\":\"Kingdee rejected payload\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-11");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Kingdee rejected payload");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseFailedJsonStatusAsCompletedFailureWithoutRejectedKeyword() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-12");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseExplicitCompletedAndSuccessJsonFields() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback/status", exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-13");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseNestedDataStatusResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-14");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedCompletedFailureUnderStatusSuccessWrapperAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-14S");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepDataCompletedFalseAndSuccessTrueOpenUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"completed\":false,\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-14T");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataCompletedSuccessUnderStatusSuccessWrapperAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-14U");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseNestedResultStatusResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-15");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessStatusOverGenericTopLevelSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-16");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverGenericNestedDataResultUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-16P");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverGenericNestedDataResultUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-16Q");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessFlagsOverGenericNestedDataResultUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-16R");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessMessageOverGenericTopLevelMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"line capacity exceeded\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-17");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("line capacity exceeded");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessFailureOverGenericTopLevelCompletedSuccessFlags() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverGenericNestedDataResultUnderCompletedSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18R");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverGenericNestedDataResultUnderCompletedSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18R1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverSuccessTrueDataWrapperUnderCompletedSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18RS");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessFailureOverGenericTopLevelSuccessFlag() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18S");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverGenericNestedDataResultUnderSuccessOnlyRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18T");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverSuccessTrueDataWrapperUnderSuccessOnlyRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18U");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessOverSuccessTrueDataWrapperUnderSuccessOnlyRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18V");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverSuccessTrueDataWrapperUnderSuccessOnlyRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18W");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessMessageOverSuccessTrueDataWrapperUnderSuccessOnlyRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18X");

            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessFailureOverIncompleteTopLevelWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":false,\"success\":false,\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverIncompleteDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":false,\"success\":false,\"message\":\"processing\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18D");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessFailureOverOpenTopLevelStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"processing\",\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-18B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxFailedStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxErrorStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"error\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxUnsuccessfulStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"unsuccessful\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxNotSuccessfulStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"not_successful\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19C");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxNotCompletedStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"not_completed\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19D");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxNotSucceededStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"not_succeeded\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSucceededStatusWithSpaceAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not succeeded\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotAcceptedStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not-accepted\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E15");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotAcceptedStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"notAccepted\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E155");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSubmittedStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"notSubmitted\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E165");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSuccessfulStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"notSuccessful\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E175");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotCompletedStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"notCompleted\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E185");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSucceededStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"notSucceeded\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E195");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSubmittedStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not-submitted\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E16");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSuccessfulStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not-successful\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E17");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotCompletedStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not-completed\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E18");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonNotSucceededStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"not-succeeded\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E19");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTimedOutStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"timedOut\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E20");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTimedOutStatusAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"timed_out\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTimedOutStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"timed-out\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19E3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNon2xxFailureStatusResponseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 500, "{\"status\":\"failure\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19F");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldParseNestedDataResultStatusResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-20");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReadExternalRequestIdFromNestedDataResultResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"result\":{\"requestId\":\"REQ-21\",\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedRequestIdOverGenericDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-21B\",\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21B");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageOverGenericDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-21C\",\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepNestedRequestIdOnWritebackResponseWithTopLevelBusinessFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-21F\",\"message\":\"accepted\"}},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21F");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-21F\",\"message\":\"accepted\"}},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdWhenDataResultCarriesWritebackFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-21G\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21G");
            assertThat(result.message()).isEqualTo("{\"data\":{\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-21G\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdWhenGenericDataWrapperContainsDataResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-21H\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21H");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-21H\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenDataResultCarriesFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"requestId\":\"REQ-21I\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21I");
            assertThat(result.message()).isEqualTo("{\"data\":{\"requestId\":\"REQ-21I\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataMessageWhenDataResultCarriesFailureWithoutOwnMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"message\":\"validation error\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"message\":\"validation error\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"message\":\"old message\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenGenericDataWrapperContainsDataResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-21J\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21J");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-21J\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenTopLevelSuccessWrapperContainsDataResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"requestId\":\"REQ-21K\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21K");
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"requestId\":\"REQ-21K\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenCompletedSuccessWrapperContainsDataResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21L\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21L");
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21L\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenStatusSuccessWrapperContainsDataResultFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21M\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21M");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21M\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenDataSuccessFalseWithoutWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"requestId\":\"REQ-21N0\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N0");
            assertThat(result.message()).isEqualTo("{\"data\":{\"requestId\":\"REQ-21N0\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21N1\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N1");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21N1\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"requestId\":\"REQ-21N2\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N2");
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"requestId\":\"REQ-21N2\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21N3\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N3");
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21N3\",\"success\":false,\"result\":{\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultRequestIdOverTopLevelRequestIdWhenDataSuccessFalseWithoutWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N4\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N4");
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N4\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdWhenDataAcceptedCompletedSuccessWrapperProvidesRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"completed\":true,\"success\":true,\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-21NAC\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NAC");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdWhenDataAcceptedStatusWrapsCompletedSuccessPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-21NA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverDataAcceptedStatusNestedRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-21NAR\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NAR");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdWhenRootAcceptedStatusWrapsCompletedSuccessDataPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"data\":{\"completed\":true,\"success\":true,\"message\":\"submitted\"},\"result\":{\"requestId\":\"REQ-21NAA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NAA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultRequestIdOverTopLevelRequestIdWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N5\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N5");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N5\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultRequestIdOverTopLevelRequestIdWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N6\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N6");
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N6\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultRequestIdOverTopLevelRequestIdWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N7\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N7");
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"result\":{\"requestId\":\"REQ-21N7\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureRequestIdOverNestedAcceptedAliasRequestIdWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"requestId\":\"REQ-21N8A\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N8A");
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"requestId\":\"REQ-21N8A\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureRequestIdOverNestedSuccessRequestIdWhenDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"requestId\":\"REQ-21N8\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N8");
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"requestId\":\"REQ-21N8\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureRequestIdOverNestedSuccessRequestIdWhenRootStatusSuccessWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"requestId\":\"REQ-21N9\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N9");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"success\":false,\"requestId\":\"REQ-21N9\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureRequestIdOverNestedSuccessRequestIdWhenRootSuccessTrueWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"requestId\":\"REQ-21NA\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NA");
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"success\":false,\"requestId\":\"REQ-21NA\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataFailureRequestIdOverNestedSuccessRequestIdWhenCompletedSuccessWrapperWrapsDataSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"requestId\":\"REQ-21NB\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21NB");
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"requestId\":\"REQ-21NB\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataSuccessFalseWithoutOwnRequestIdEvenIfNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataSuccessFalseOwnRequestIdIsOnlyWhitespaceEvenIfNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootStatusSuccessWrapsDataFailureWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootSuccessTrueWrapsDataFailureWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataFailureWithoutOwnRequestIdButNestedStatusSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataFailureWithoutOwnRequestIdButNestedCompletedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataFailureWithoutOwnRequestIdButNestedOpenSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"completed\":false,\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenCompletedSuccessWrapperWrapsDataFailureWithoutOwnRequestIdButNestedStatusSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootStatusSuccessWrapsDataFailureWithoutOwnRequestIdButNestedStatusSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootSuccessTrueWrapsDataFailureWithoutOwnRequestIdButNestedAcceptedStatusPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootSuccessTrueWrapsDataFailureWithoutOwnRequestIdButNestedSubmittedStatusPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootAcceptedWithoutErrorsWrapsDataFailureWithoutOwnRequestIdButNestedSubmittedStatusPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootAcceptedStatusWrapsDataFailureWithoutOwnRequestIdButNestedSubmittedStatusPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"accepted\",\"message\":\"accepted\",\"data\":{\"success\":false,\"message\":\"validation error\",\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataStatusFailedWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataStatusFailedWithoutOwnRequestIdButNestedStatusSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataStatusRejectedWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"rejected\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"rejected\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenDataStatusRejectedWithoutOwnRequestIdButNestedAcceptedStatusPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"rejected\",\"message\":\"validation error\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"rejected\",\"message\":\"validation error\",\"result\":{\"status\":\"accepted\",\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootStatusSuccessWrapsDataStatusFailedWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenRootSuccessTrueWrapsDataStatusFailedWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenCompletedSuccessWrapperWrapsDataStatusFailedWithoutOwnRequestIdButNestedSuccessPayloadAndTopLevelResultProvideOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"status\":\"failed\",\"message\":\"validation error\",\"result\":{\"success\":true,\"requestId\":\"REQ-SUCCESS\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenTopLevelSuccessWrapperContainsDataResultSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"requestId\":\"REQ-21N\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21N");
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"requestId\":\"REQ-21N\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenGenericDataWrapperContainsDataResultSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-21O\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21O");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-21O\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenCompletedSuccessWrapperContainsDataResultSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21P\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21P");
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"data\":{\"requestId\":\"REQ-21P\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestIdWhenStatusSuccessWrapperContainsDataResultSuccessFalse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21Q\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-21Q");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"data\":{\"requestId\":\"REQ-21Q\",\"result\":{\"success\":false,\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataMessageWhenTopLevelSuccessWrapperContainsDataResultSuccessFalseWithoutOwnMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"message\":\"validation error\",\"result\":{\"success\":false}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"data\":{\"message\":\"validation error\",\"result\":{\"success\":false}},\"result\":{\"message\":\"old message\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataMessageOverTopLevelMessageWhenGenericDataWrapperContainsDataResultSuccessFalseWithoutOwnMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"validation error\",\"result\":{\"success\":false}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultBusinessFailureOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultRejectedFailureOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"rejected\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22P1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverSuccessTrueDataWrapperUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22PS");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverSuccessTrueDataWrapperUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22PT");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverSuccessTrueDataWrapperUnderStatusSuccessRootWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22PT");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\"},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22P");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatTopLevelRejectedStatusAsBusinessFailureOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\"},\"result\":{\"status\":\"rejected\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22Q1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22Q");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessFlagsOverGenericDataStatusSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\"},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22QR");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelProcessingOverCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\"},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22R");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22S");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22SS");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedMessageOverCompletedSuccessDataWrapperMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22ST");

            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultGenericSuccessOverCompletedSuccessDataWrapperOnlyWhenNoTopLevelBusinessStatusExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22T");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferSuccessOnlyDataResultOverCompletedSuccessDataWrapperOnlyWhenNoTopLevelBusinessStatusExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22U");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedSuccessDataResultOnlyWhenNoTopLevelBusinessStatusExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"completed\":true,\"success\":true,\"message\":\"accepted\"}},\"result\":{\"status\":\"processing\",\"success\":true,\"message\":\"processing\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22V");

            assertThat(result.completed()).isFalse();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessFlagsOverGenericNestedDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22VW");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverGenericNestedDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"success\",\"message\":\"accepted\"}},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22VX");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessFlagsOverSuccessTrueDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"success\":true,\"message\":\"accepted\"}},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22VY");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataMessageWhenDataResultCarriesRejectedFailureWithoutOwnMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"processing\",\"message\":\"validation error\",\"result\":{\"status\":\"rejected\",\"success\":false}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22A0");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultBusinessFailureOverOpenDataStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"processing\",\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultBusinessFailureOverIncompleteDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":false,\"success\":false,\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataResultBusinessMessageOverOuterSuccessFalseMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverSuccessFalseDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"processing\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22B1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelBusinessFailureOverSuccessTrueDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22B2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedSuccessFlagsOverSuccessTrueDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"completed\":true,\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22B3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelCompletedStatusOverSuccessTrueDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\"},\"result\":{\"status\":\"completed\",\"success\":true,\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22B4");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBusinessMessageOverTopLevelSuccessFalseMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":false,\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22C");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureMessageOverTopLevelSuccessFalseMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":false,\"message\":\"processing\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22D");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferRootFailureMessageWhenStatusSuccessAndSuccessFalseWrapNestedSuccessPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"success\":false,\"message\":\"validation error\",\"result\":{\"success\":true,\"message\":\"accepted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22D2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTerminalFailedStatusWithSpaceAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"terminal failed\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19F1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTerminalFailedStatusWithHyphenAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"terminal-failed\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19F3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTerminalFailedStatusWithCamelCaseAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"terminalFailed\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19F4");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferRootFailureMessageWhenTerminalFailedWrapsNestedFailurePayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"terminal_failed\",\"message\":\"processing\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-19F2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("processing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenQueryStatusBusinessFailureHasNoMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"failed\",\"success\":false}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"failed\",\"success\":false}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenNestedQueryStatusBusinessFailureHasNoMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E1");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataAcceptedStatusContainsNestedBusinessFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E2A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenCompletedSuccessWrapperContainsNestedBusinessFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootStatusSuccessWrapperContainsAcceptedDataFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"status\":\"accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E3A");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"status\":\"accepted\",\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsNestedBusinessFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataCompletedSuccessWrapperContainsNestedBusinessFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E4");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsNestedCompletedFailureWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E5");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":false}},\"result\":{\"requestId\":\"REQ-OLD\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsNestedBusinessFailureWithoutMessageEvenIfTopLevelResultProvidesMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E6");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false}},\"result\":{\"message\":\"old message\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsNestedBusinessFailureWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"   \"}},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E6B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"   \"}},\"result\":{\"message\":\"old message\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsTopLevelNestedBusinessFailureWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E6C");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"   \"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenRootSuccessWrapperContainsDataFailureWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":false,\"message\":\"   \"},\"result\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E6D");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"success\":true,\"message\":\"request accepted\",\"data\":{\"success\":false,\"message\":\"   \"},\"result\":{\"message\":\"old message\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultBusinessFailureOverRootSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-22E6F");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatExplicitBusinessFailureInWritebackResponseAsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatExplicitSuccessFalseWithoutStatusAsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":false,\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNestedDataResultSuccessFalseWithoutStatusAsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"result\":{\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSuccessFalseWithoutStatusAsFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataStatusOkWithNestedCompletedFailureAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatOkStatusWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"ok\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataOkStatusWithTopLevelSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataAcceptedStatusWithTopLevelSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\"},\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10ACK2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSubmittedStatusWithTopLevelSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"submitted\",\"message\":\"submitted\"},\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10SUB2");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSubmittedStatusWithTopLevelFailedResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"submitted\",\"message\":\"submitted\"},\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10SUB3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataOkStatusWithNestedSucceededResultAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\",\"result\":{\"status\":\"succeeded\",\"message\":\"writeback completed\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK22");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSucceededStatusWithNestedFailurePayloadAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"succeeded\",\"message\":\"accepted\",\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK23");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataSucceededStatusWrapsTopLevelSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"succeeded\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10S2N");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"succeeded\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataSuccessTrueWithNestedFailurePayloadAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"accepted\",\"result\":{\"status\":\"failed\",\"success\":false,\"message\":\"validation error\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK24");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataOkStatusWithTopLevelCompletedFailureAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"completed\":true,\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK3");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataOkStatusWithTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK4");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataOkStatusWrapsTopLevelSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK4N");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnBlankMessageWhenDataOkStatusWrapsTopLevelSuccessFalseResultWithBlankMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK4B");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataOkStatusWithoutWrapperMessageWrapsTopLevelSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\"},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK4NM");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"ok\"},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRootSuccessWrapperWithAcceptedDataResultAndTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1SF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRootStatusSuccessWrapperWithAcceptedDataResultAndTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1RF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataCompletedSuccessWrapperWithAcceptedResultAndTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10DCF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatCompletedSuccessWrapperWithAcceptedDataResultAndTopLevelSuccessFalseResultAsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"status\":\"success\",\"success\":false,\"message\":\"validation error\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10C1CF");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("validation error");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnResponseBodyWhenDataAcceptedStatusWrapsTopLevelSuccessFalseResultWithoutMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10A1N");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\"},\"result\":{\"status\":\"success\",\"success\":false}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatDataOkStatusWithTopLevelCompletedStatusAsCompletedSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback/status",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"ok\",\"message\":\"accepted\"},\"result\":{\"status\":\"completed\",\"message\":\"writeback completed\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackStatusResult result = client.queryStatus("REQ-10OK5");

            assertThat(result.completed()).isTrue();
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("writeback completed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotTreatNestedSuccessTrueWithoutRequestIdAsSuccessfulWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"result\":{\"success\":true,\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageOverGenericTopLevelSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-30\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageUnderWhitespacePaddedSuccessWrapperForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\" success \",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-30W\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30W");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageUnderStatusOkWrapperForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"ok\",\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-30OK\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30OK");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenDataAcceptedStatusWrapsWritebackSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\"},\"result\":{\"requestId\":\"REQ-30DA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30DA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenDataSubmittedStatusWrapsWritebackSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"submitted\",\"message\":\"submitted\"},\"result\":{\"requestId\":\"REQ-30DS\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30DS");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenDataRequestAcceptedStatusWrapsWritebackSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"request accepted\",\"message\":\"request accepted\"},\"result\":{\"requestId\":\"REQ-30DR\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30DR");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenCompletedSuccessWrapperWrapsAcceptedDataResultForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30RC\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30RC");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenRootStatusSuccessWrapsAcceptedDataResultForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30RT\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30RT");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultFieldsWhenRootSuccessOnlyWrapsAcceptedDataResultForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"data\":{\"result\":{\"status\":\"accepted\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30RS\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30RS");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageUnderAcceptedStatusWrapperForWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"requestId\":\"REQ-30AC\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30AC");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdButFallBackToDataResultMessageUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"result\":{\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-47B\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47B");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToDataResultRequestIdUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"result\":{\"requestId\":\"REQ-47C\"}},\"result\":{\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIgnoreBlankDataResultMessageAndFallBackToTopLevelResultMessageUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"result\":{\"requestId\":\"REQ-47D\",\"message\":\"   \"}},\"result\":{\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverLaterDataUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"result\":{\"requestId\":\"REQ-47\",\"message\":\"submitted\"},\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferSelectedResultMessageWhenLaterDataMessageIsBlankUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"result\":{\"requestId\":\"REQ-47E\",\"message\":\"submitted\"},\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"   \"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47E");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverDataResultUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"result\":{\"requestId\":\"REQ-47A\",\"message\":\"submitted\"},\"data\":{\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47A");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"success\",\"message\":\"request accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-30D\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-30D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedResultRequestIdOverTopLevelRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"requestId\":\"REQ-OLD\",\"status\":\"success\",\"result\":{\"requestId\":\"REQ-31\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-31");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"requestId\":\"REQ-OLD\",\"data\":{\"requestId\":\"REQ-32\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-32");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataMessageOverTopLevelMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"message\":\"request accepted\",\"data\":{\"requestId\":\"REQ-33\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-33");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferHeaderRequestIdOverBodyRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-34");
            respond(exchange, 200, "{\"requestId\":\"REQ-BODY\",\"message\":\"submitted\"}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-34");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIgnoreBlankHeaderAndPreferNestedBodyRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "   ");
            respond(exchange, 200, "{\"requestId\":\"REQ-OLD\",\"data\":{\"result\":{\"requestId\":\"REQ-35\",\"message\":\"submitted\"}}}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-35");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTrimNestedDataResultRequestIdBeforeReturningIt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "   ");
            respond(exchange, 200, "{\"requestId\":\"REQ-OLD\",\"data\":{\"result\":{\"requestId\":\"  REQ-35B  \",\"message\":\"submitted\"}}}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-35B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldFallBackToTopLevelRequestIdWhenBlankHeaderAndNestedStructuralRequestIdsAreBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "   ");
            respond(exchange, 200, "{\"requestId\":\"REQ-35C\",\"data\":{\"result\":{\"requestId\":\"   \",\"message\":\"submitted\"}}}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-35C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedBodyMessageWhenHeaderProvidesRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-36");
            respond(exchange, 200, "{\"message\":\"request accepted\",\"result\":{\"message\":\"submitted\"}}" );
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-36");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTrimNestedResultRequestIdBeforeReturningIt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"requestId\":\"REQ-OLD\",\"result\":{\"requestId\":\"  REQ-36B  \",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-36B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedSubmittedMessageOverCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-37\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-37");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTrimHeaderRequestIdBeforeReturningIt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "  REQ-38  ");
            respond(exchange, 200, "{\"message\":\"submitted\"}");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTrimBodyRequestIdBeforeReturningIt() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"requestId\":\"  REQ-38B  \",\"message\":\"submitted\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextWithoutErrorsResponseAsSuccessfulWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38A");
            respond(exchange, 200, "accepted without errors");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38A");
            assertThat(result.message()).isEqualTo("accepted without errors");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextSubmittedResponseAsSuccessfulWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38C");
            respond(exchange, 200, "submitted");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextAcceptedResponseAsSuccessfulWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38D");
            respond(exchange, 200, "accepted");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38D");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextRequestAcceptedResponseAsSuccessfulWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38E");
            respond(exchange, 200, "request accepted");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38E");
            assertThat(result.message()).isEqualTo("request accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextRejectedResponseAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            respond(exchange, 200, "rejected");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("rejected");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextFailureResponseWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38F");
            respond(exchange, 200, "failure");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38F");
            assertThat(result.message()).isEqualTo("failure");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextNotAcceptedResponseWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38G");
            respond(exchange, 200, "not accepted");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38G");
            assertThat(result.message()).isEqualTo("not accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextNotSubmittedResponseWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38H");
            respond(exchange, 200, "not submitted");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38H");
            assertThat(result.message()).isEqualTo("not submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextDeclinedResponseWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38I");
            respond(exchange, 200, "declined");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38I");
            assertThat(result.message()).isEqualTo("declined");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatPlainTextDeniedResponseWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> {
            exchange.getResponseHeaders().add("X-Request-Id", "REQ-38J");
            respond(exchange, 200, "denied");
        });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38J");
            assertThat(result.message()).isEqualTo("denied");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonDeniedStatusWithRequestIdAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"status\":\"denied\",\"requestId\":\"REQ-38K\",\"message\":\"denied\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38K");
            assertThat(result.message()).isEqualTo("{\"status\":\"denied\",\"requestId\":\"REQ-38K\",\"message\":\"denied\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatJsonTerminalFailedStatusWithCamelCaseAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"status\":\"terminalFailed\",\"requestId\":\"REQ-38L\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38L");
            assertThat(result.message()).isEqualTo("{\"status\":\"terminalFailed\",\"requestId\":\"REQ-38L\",\"message\":\"validation error\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatRootStatusSuccessWithExplicitSuccessFalseAsFailedWriteback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/k3cloud/schedule/writeback", exchange -> respond(exchange, 200, "{\"status\":\"success\",\"success\":false,\"requestId\":\"REQ-38F\",\"message\":\"validation error\"}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-38F");
            assertThat(result.message()).isEqualTo("{\"status\":\"success\",\"success\":false,\"requestId\":\"REQ-38F\",\"message\":\"validation error\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedDataResultOverCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"requestId\":\"REQ-39\",\"message\":\"submitted\"}}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-39");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferSelectedDataNodeOverTopLevelResultFallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"},\"data\":{\"requestId\":\"REQ-40\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-40");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferSelectedDataStatusNodeOverTopLevelResultFallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"completed\",\"success\":true,\"requestId\":\"REQ-40B\",\"message\":\"submitted\"},\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-40B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferSelectedTopLevelResultNodeOverDataFallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"},\"result\":{\"requestId\":\"REQ-41\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-41");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdButFallBackToNestedDataResultMessageBeforeLaterDataUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"message\":\"old message\",\"result\":{\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-43A\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-43A");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToDataResultRequestIdUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"submitted\",\"data\":{\"result\":{\"requestId\":\"REQ-43B\"}},\"result\":{}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-43B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedResultMessageOverLaterDataMessageUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-42\",\"message\":\"submitted\"},\"data\":{\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-42");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedResultRequestIdOverLaterDataRequestIdUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-43\",\"message\":\"submitted\"},\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-43");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverEarlierDataResultUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}},\"result\":{\"requestId\":\"REQ-44\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-44");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-44B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-44B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-44C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-44C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderCompletedSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"completed\":true,\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-44D\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-44D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultMessageOverDataSuccessWrapperMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"request accepted\"},\"result\":{\"requestId\":\"REQ-45\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-45");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverDataSuccessWrapperRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"request accepted\"},\"result\":{\"requestId\":\"REQ-46\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverNestedDataResultRequestIdUnderRootAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"data\":{\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-OLD\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-46A\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46A");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultMessageOverNestedDataResultMessageUnderRootAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"accepted\",\"message\":\"accepted\",\"data\":{\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-OLD\",\"message\":\"old submitted\"}},\"result\":{\"requestId\":\"REQ-46B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverNestedDataResultRequestIdUnderRootRequestAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"status\":\"request accepted\",\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"submitted\",\"requestId\":\"REQ-OLD\",\"message\":\"submitted\"}},\"result\":{\"requestId\":\"REQ-46C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverNestedDataResultRequestIdUnderDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"message\":\"request accepted\",\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-46R\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46R");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverNestedDataResultRequestIdUnderDataAcceptedWithoutErrorsWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-46RE\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RE");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultMessageOverNestedDataResultMessageUnderDataAcceptedWithoutErrorsWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old accepted\"}},\"result\":{\"requestId\":\"REQ-46RM\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RM");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdOverNestedDataCompletedFailureRequestIdUnderDataAcceptedWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-46RF\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RF");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-46RF\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelResultRequestIdWhenBlankHeaderWrapsDataAcceptedCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"requestId\":\"REQ-46RG\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RG");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"requestId\":\"REQ-46RG\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultRequestIdWhenBlankHeaderWrapsDataAcceptedCompletedFailureWithWhitespaceOwnRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"requestId\":\"   \",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-46RH\",\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RH");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"requestId\":\"   \",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-46RH\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelResultRequestIdWhenBlankHeaderWrapsDataAcceptedWithoutErrorsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"requestId\":\"REQ-46RI\",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RI");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted without errors\",\"requestId\":\"REQ-46RI\",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelResultRequestIdWhenBlankHeaderWrapsDataWithNoErrorsCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"with no errors\",\"requestId\":\"REQ-46RJ\",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RJ");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"with no errors\",\"requestId\":\"REQ-46RJ\",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferDataRequestIdOverTopLevelResultRequestIdWhenBlankHeaderWrapsDataWithoutErrorCompletedFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"without error\",\"requestId\":\"REQ-46RK\",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RK");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"without error\",\"requestId\":\"REQ-46RK\",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-OLD\",\"message\":\"validation error\"}},\"result\":{\"requestId\":\"REQ-TOP\",\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureRequestIdWhenBlankHeaderWrapsDataAcceptedAndOwnRequestIdIsWhitespaceWithoutTopLevelResultRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"requestId\":\"   \",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RL\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RL");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"requestId\":\"   \",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RL\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenBlankHeaderWrapsAcceptedAliasCompletedFailureWithoutAnyUsableStructuralRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted\",\"message\":\"accepted\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureRequestIdWhenBlankHeaderWrapsDataAcceptedWithoutErrorsAndOwnRequestIdIsWhitespaceWithoutTopLevelResultRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"requestId\":\"   \",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RN\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RN");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted without errors\",\"requestId\":\"   \",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RN\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureRequestIdWhenBlankHeaderWrapsDataWithNoErrorsAndOwnRequestIdIsWhitespaceWithoutTopLevelResultRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"with no errors\",\"requestId\":\"   \",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RO\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RO");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"with no errors\",\"requestId\":\"   \",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RO\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferNestedCompletedFailureRequestIdWhenBlankHeaderWrapsDataWithoutErrorAndOwnRequestIdIsWhitespaceWithoutTopLevelResultRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"without error\",\"requestId\":\"   \",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RP\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isEqualTo("REQ-46RP");
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"without error\",\"requestId\":\"   \",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"REQ-46RP\",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenBlankHeaderWrapsAcceptedWithoutErrorsCompletedFailureWithoutAnyUsableStructuralRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"accepted without errors\",\"message\":\"accepted without errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenBlankHeaderWrapsWithNoErrorsCompletedFailureWithoutAnyUsableStructuralRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"with no errors\",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"with no errors\",\"message\":\"with no errors\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepBlankRequestIdWhenBlankHeaderWrapsWithoutErrorCompletedFailureWithoutAnyUsableStructuralRequestId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> {
                    exchange.getResponseHeaders().add("X-Request-Id", " ");
                    respond(exchange, 200, "{\"data\":{\"status\":\"without error\",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
                });
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isFalse();
            assertThat(result.externalRequestId()).isBlank();
            assertThat(result.message()).isEqualTo("{\"data\":{\"status\":\"without error\",\"message\":\"without error\",\"result\":{\"completed\":true,\"success\":false,\"requestId\":\"   \",\"message\":\"validation error\"}},\"result\":{\"message\":\"submitted\"}}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverLaterDataUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"},\"result\":{\"requestId\":\"REQ-48AA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48AA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverAcceptedDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-48AB\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48AB");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"request accepted\"},\"result\":{\"requestId\":\"REQ-47\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-47");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}},\"result\":{\"requestId\":\"REQ-48\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-48B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-48C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderCompletedSuccessDataWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"completed\":true,\"success\":true,\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-48D\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-48D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverDataResultUnderGenericDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}},\"result\":{\"requestId\":\"REQ-49\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderDataSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"success\":true,\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49D\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdButFallBackToDataResultMessageUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49DE\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DE");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToDataResultRequestIdUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"requestId\":\"REQ-49DF\"}},\"result\":{\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DF");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdButFallBackToNestedDataResultMessageBeforeLaterDataUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"message\":\"old message\",\"result\":{\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49DC\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DC");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToNestedDataResultRequestIdBeforeLaterDataUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"result\":{\"requestId\":\"REQ-49DD\"}},\"result\":{\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DD");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToDataRequestIdUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-49DD\"},\"result\":{\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DD");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverLaterDataUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"},\"result\":{\"requestId\":\"REQ-49DB\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DB");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverDataResultUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49DA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49DA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49E\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49E");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49F\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49F");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderDataStatusSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"data\":{\"status\":\"success\",\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-49G\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-49G");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverLaterDataUnderGenericTopLevelSuccessWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"result\":{\"requestId\":\"REQ-50\",\"message\":\"submitted\"},\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"old message\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelRequestIdButFallBackToNestedDataResultMessageBeforeLaterDataUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"message\":\"old message\",\"result\":{\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-50AE\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50AE");
            assertThat(result.message()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToNestedDataResultRequestIdBeforeLaterDataUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"submitted\",\"data\":{\"requestId\":\"REQ-OLD\",\"result\":{\"requestId\":\"REQ-50AF\"}},\"result\":{}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50AF");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTopLevelMessageButFallBackToDataResultRequestIdUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"submitted\",\"data\":{\"result\":{\"requestId\":\"REQ-50AF\"}},\"result\":{}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50AF");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverLaterDataUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"},\"result\":{\"requestId\":\"REQ-50AA\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50AA");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverDataResultUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-50A\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50A");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverSuccessTrueDataResultUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-50B\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50B");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverStatusSuccessDataResultUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"status\":\"success\",\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-50C\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50C");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreferTopLevelResultOverCompletedSuccessDataResultUnderSuccessOnlyWrapper() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/k3cloud/schedule/writeback",
                exchange -> respond(exchange, 200, "{\"success\":true,\"message\":\"request accepted\",\"data\":{\"result\":{\"completed\":true,\"success\":true,\"requestId\":\"REQ-OLD\",\"message\":\"accepted\"}},\"result\":{\"requestId\":\"REQ-50D\",\"message\":\"submitted\"}}"));
        server.start();
        try {
            KingdeeProperties properties = properties(server);
            HttpKingdeeWritebackClient client = new HttpKingdeeWritebackClient(properties, new ObjectMapper());

            KingdeeWritebackResult result = client.writeback(new KingdeeWritebackPayload(
                    "draft-1",
                    java.util.List.of(new KingdeeWritebackItemPayload(
                            "TASK-001",
                            "LINE-A",
                            "2026-04-24T08:00:00Z",
                            "2026-04-24T10:00:00Z",
                            "1"))));

            assertThat(result.success()).isTrue();
            assertThat(result.externalRequestId()).isEqualTo("REQ-50D");
            assertThat(result.message()).isEqualTo("submitted");
        } finally {
            server.stop(0);
        }
    }

    private static KingdeeProperties properties(HttpServer server) {
        return new KingdeeProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "demo-app",
                "demo-secret",
                "/k3cloud/schedule/writeback",
                "/k3cloud/schedule/writeback/status",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
