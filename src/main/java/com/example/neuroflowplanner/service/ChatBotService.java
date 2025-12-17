package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.util.ConfigManager;
import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;

public class ChatBotService {
    
    private final String API_URL;
    private final String MODEL;
    private final HttpClient client;
    
    public ChatBotService() {
        API_URL = ConfigManager.getProperty("api.url");
        MODEL = ConfigManager.getProperty("api.model");
        client = createTrustAllClient();
    }
    
    private HttpClient createTrustAllClient() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String type) {}
                    public void checkServerTrusted(X509Certificate[] certs, String type) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
        } catch (Exception e) {
            return HttpClient.newHttpClient();
        }
    }
    
    public CompletableFuture<String> sendMessage(String message) {
        String json = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "Ты — умный ассистент планировщика задач NeuroFlow. Помогай пользователю с планированием, приоритизацией и организацией задач. Отвечай кратко и по делу на русском языке."},
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
            """.formatted(MODEL, escapeJson(message));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    return extractContent(response.body());
                } else {
                    return "Ошибка API: " + response.statusCode() + "\n" + response.body();
                }
            })
            .exceptionally(e -> "Ошибка соединения: " + e.getMessage());
    }
    
    private String extractContent(String json) {
        // Ollama format: {"message":{"content":"..."}}
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return "Не удалось получить ответ: " + json;
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return unescapeJson(json.substring(start, end));
    }
    
    private String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> { sb.append('\n'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'u' -> {
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        } else sb.append(c);
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
