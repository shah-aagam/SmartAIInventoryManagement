package com.example.ZCHackathon.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public LLMGateway(
            ObjectMapper objectMapper,
            @Value("${llm.base-url:https://api.groq.com/openai}") String baseUrl,
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model:llama-3.3-70b-versatile}") String model) {

        log.info(
                "[LLM-CONFIG] Initializing Groq Gateway | baseUrl={} | model={} | apiKeyConfigured={}",
                baseUrl,
                model,
                apiKey != null && !apiKey.isBlank()
        );

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;

        log.info("[LLM-CONFIG] Groq LLM Gateway initialized successfully");
    }

    public String callLLM(String prompt) {

        log.info(
                "[LLM-1] callLLM invoked | model={} | promptLength={}",
                model,
                prompt != null ? prompt.length() : 0
        );

        if (apiKey == null || apiKey.isBlank()) {
            log.error("[LLM-ERROR] Groq API key is not configured");
            throw new IllegalStateException("LLM_API_KEY is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        try {
            log.info("[LLM-2] Sending request to Groq API...");

            String response = http.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("[LLM-DEBUG-RAW] Raw Groq API response:\n{}", response);

            if (response == null || response.isBlank()) {
                log.error("[LLM-ERROR] Groq returned empty response");
                throw new IllegalStateException("LLM returned an empty response");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (!content.isTextual()) {
                log.error("[LLM-ERROR] Missing choices[0].message.content in Groq response");
                throw new IllegalStateException("LLM response missing choices[0].message.content");
            }

            return content.asText();

        } catch (RestClientResponseException e) {
            log.error(
                    "[LLM-HTTP-ERROR] Groq request failed | status={} | body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );
            throw e;
        } catch (JsonProcessingException e) {
            log.error("[LLM-ERROR] Failed to parse Groq JSON response", e);
            throw new IllegalStateException("Unable to parse LLM response payload", e);
        } catch (Exception e) {
            log.error("[LLM-ERROR] Unexpected error calling Groq API", e);
            throw new RuntimeException("Unexpected error during LLM invocation", e);
        }
    }
}