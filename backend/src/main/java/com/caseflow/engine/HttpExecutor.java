package com.caseflow.engine;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class HttpExecutor {

    private final OkHttpClient client;

    public HttpExecutor() {
        this.client = createTrustAllClient();
    }

    public HttpResult execute(ExecuteRequest req) {
        long start = System.currentTimeMillis();
        try {
            Request.Builder rb = new Request.Builder().url(req.url());
            if (req.headers() != null) {
                req.headers().forEach(rb::addHeader);
            }
            RequestBody body = buildBody(req);
            rb.method(req.method().toUpperCase(), body);

            try (Response resp = client.newCall(rb.build()).execute()) {
                String respBody = resp.body() != null ? resp.body().string() : "";
                Map<String, String> respHeaders = new HashMap<>();
                for (String name : resp.headers().names()) {
                    respHeaders.put(name, resp.header(name));
                }
                long duration = System.currentTimeMillis() - start;
                return new HttpResult(resp.code(), respHeaders, respBody, duration, null);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("[HttpExecutor] request failed: {} {}", req.method(), req.url(), e);
            return new HttpResult(0, Map.of(), "", duration, e.getMessage());
        }
    }

    private RequestBody buildBody(ExecuteRequest req) {
        if (req.method().equalsIgnoreCase("GET") || req.method().equalsIgnoreCase("HEAD")) {
            return null;
        }
        String bodyType = req.bodyType();
        String bodyContent = req.bodyContent();
        if (bodyType == null || "NONE".equals(bodyType) || bodyContent == null || bodyContent.isBlank()) {
            return RequestBody.create("", MediaType.parse("text/plain"));
        }
        return switch (bodyType) {
            case "JSON" -> RequestBody.create(bodyContent, MediaType.parse("application/json; charset=utf-8"));
            case "XML" -> RequestBody.create(bodyContent, MediaType.parse("application/xml; charset=utf-8"));
            case "FORM" -> {
                FormBody.Builder fb = new FormBody.Builder();
                for (String pair : bodyContent.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) fb.add(kv[0].trim(), kv[1].trim());
                }
                yield fb.build();
            }
            default -> RequestBody.create(bodyContent, MediaType.parse("text/plain; charset=utf-8"));
        };
    }

    private OkHttpClient createTrustAllClient() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslCtx.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((h, s) -> true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client", e);
        }
    }

    public record ExecuteRequest(String method, String url, Map<String, String> headers,
                                  String bodyType, String bodyContent) {}

    public record HttpResult(int statusCode, Map<String, String> headers, String body,
                              long durationMs, String error) {
        public boolean isSuccess() { return error == null; }
    }
}
