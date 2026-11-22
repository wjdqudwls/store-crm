package com.crmpos.seed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * seed 프로필 도구가 자기 서버의 API를 부르는 작은 HTTP 클라이언트. 다른 모듈의 코드를 import하지 않고 진짜 API만
 * 거치므로 모듈 경계(ADR-012)를 지키고, 입력 검사·권한·이벤트가 실제와 똑같이 돈다. 응답 본문은 로그에 남기지 않는다(NFR-04b).
 */
final class SeedApi {

    record Response(int status, JsonNode body) {
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String base;

    SeedApi(ObjectMapper mapper, int port) {
        this.mapper = mapper;
        this.base = "http://localhost:" + port + "/api/v1";
    }

    /** 로그인해서 Access 토큰을 돌려준다. */
    String login(String loginId, String password) {
        Response res = send("POST", "/auth/login", null, Map.of("loginId", loginId, "password", password), null);
        if (res.status() != 200) {
            throw new IllegalStateException("로그인 실패 status=" + res.status());
        }
        return res.body().get("accessToken").asString();
    }

    Response get(String path, String token) {
        return send("GET", path, token, null, null);
    }

    Response post(String path, String token, Object body, String idempotencyKey) {
        return send("POST", path, token, body, idempotencyKey);
    }

    private Response send(String method, String path, String token, Object body, String idempotencyKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
            HttpResponse<String> res = client.send(builder.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = res.body() == null || res.body().isBlank() ? null : mapper.readTree(res.body());
            return new Response(res.statusCode(), json);
        } catch (IOException e) {
            throw new IllegalStateException("요청 실패 " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("중단됨", e);
        }
    }
}
