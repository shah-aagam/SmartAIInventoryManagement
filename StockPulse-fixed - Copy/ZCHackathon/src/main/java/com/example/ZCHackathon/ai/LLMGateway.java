// package com.example.ZCHackathon.ai;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.MediaType;
// import org.springframework.stereotype.Component;
// import org.springframework.web.client.RestClient;
// import org.springframework.http.client.SimpleClientHttpRequestFactory;

// import java.time.Duration;

// import java.util.List;
// import java.util.Map;

// @Component
// public class LLMGateway {
//     private final RestClient http;
//     private final ObjectMapper objectMapper;
//     private final String apiKey;
//     private final String model;
//     private final String productHeader;
//     private final String cookie;

//     public LLMGateway(
//             ObjectMapper objectMapper,
//             @Value("${llm.base-url}") String baseUrl,
//             @Value("${llm.api-key:}") String apiKey,
//             @Value("${llm.model:qwen-cursor}") String model,
//             @Value("${llm.product:PC1}") String productHeader,
//             @Value("${llm.cookie:}") String cookie) {
//         SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//         factory.setConnectTimeout(Duration.ofSeconds(5));
//         factory.setReadTimeout(Duration.ofSeconds(25));
//         this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
//         this.objectMapper = objectMapper;
//         this.apiKey = apiKey;
//         this.model = model;
//         this.productHeader = productHeader;
//         this.cookie = cookie;
//     }

//     public String callLLM(String prompt) {
//         if (apiKey == null || apiKey.isBlank()) {
//             throw new IllegalStateException("LLM_API_KEY is not configured");
//         }

//         Map<String, Object> body = Map.of(
//                 "model", model,
//                 "messages", List.of(
//                         Map.of("role", "user", "content", prompt)
//                 )
//         );

//         var request = http.post()
//                 .uri("/v1/chat/completions")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .header("Authorization", "Bearer " + apiKey)
//                 .header("product", productHeader)
//                 .body(body);

//         if (cookie != null && !cookie.isBlank()) {
//             request.header("Cookie", cookie);
//         }

//         String response = request.retrieve().body(String.class);
//         if (response == null || response.isBlank()) {
//             throw new IllegalStateException("LLM returned an empty response");
//         }

//         try {
//             JsonNode root = objectMapper.readTree(response);
//             JsonNode content = root.path("choices").path(0).path("message").path("content");
//             if (!content.isTextual()) {
//                 throw new IllegalStateException("LLM response did not contain choices[0].message.content");
//             }
//             return content.asText();
//         } catch (Exception e) {
//             throw new IllegalStateException("Unable to parse LLM gateway response", e);
//         }
//     }
// }

package com.example.ZCHackathon.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class LLMGateway {

    private static final Logger log =
            LoggerFactory.getLogger(LLMGateway.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String model;
    private final String productHeader;
    private final String cookie;

    public LLMGateway(
            ObjectMapper objectMapper,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.api-key:}") String apiKey,
            @Value("${llm.model:qwen-cursor}") String model,
            @Value("${llm.product:PC1}") String productHeader,
            @Value("${llm.cookie:}") String cookie) {

        log.info(
                "[LLM-CONFIG] Initializing | baseUrl={} | model={} | product={} | apiKeyConfigured={} | cookieConfigured={}",
                baseUrl,
                model,
                productHeader,
                apiKey != null && !apiKey.isBlank(),
                cookie != null && !cookie.isBlank()
        );

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.productHeader = productHeader;
        this.cookie = cookie;

        log.info("[LLM-CONFIG] LLM Gateway initialized successfully");
    }

    public String callLLM(String prompt) {

        log.info(
                "[LLM-1] callLLM invoked | model={} | promptLength={}",
                model,
                prompt != null ? prompt.length() : 0
        );

        if (apiKey == null || apiKey.isBlank()) {

            log.error("[LLM-ERROR] API key is not configured");

            throw new IllegalStateException(
                    "LLM_API_KEY is not configured"
            );
        }

        Map<String, Object> body = Map.of(
                "model",
                model,
                "messages",
                List.of(
                        Map.of(
                                "role",
                                "user",
                                "content",
                                prompt
                        )
                )
        );

        log.info(
                "[LLM-2] Request prepared | endpoint=/v1/chat/completions | model={}",
                model
        );

        var request = http.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .header(
                        "product",
                        productHeader
                )
                .body(body);

        if (cookie != null && !cookie.isBlank()) {

            request.header("Cookie", cookie);

            log.info("[LLM-3] Cookie header attached");

        } else {

            log.info("[LLM-3] No cookie configured");
        }

        try {

            log.info(
                    "[LLM-4] Sending HTTP request to LiteLLM..."
            );

            String response = request
                    .retrieve()
                    .body(String.class);

            log.info(
                    "[LLM-5] LiteLLM response received | responseLength={}",
                    response != null ? response.length() : 0
            );

            if (response == null || response.isBlank()) {

                log.error(
                        "[LLM-ERROR] LiteLLM returned empty response"
                );

                throw new IllegalStateException(
                        "LLM returned an empty response"
                );
            }

            // TEMPORARY DEBUGGING LOG
            log.info(
                    "[LLM-DEBUG] Raw LiteLLM response={}",
                    response
            );

            log.info(
                    "[LLM-6] Parsing LiteLLM response"
            );

            try {

                JsonNode root =
                        objectMapper.readTree(response);

                JsonNode content =
                        root
                                .path("choices")
                                .path(0)
                                .path("message")
                                .path("content");

                if (!content.isTextual()) {

                    log.error(
                            "[LLM-ERROR] Missing choices[0].message.content"
                    );

                    throw new IllegalStateException(
                            "LLM response did not contain choices[0].message.content"
                    );
                }

                String contentText = content.asText();

                log.info(
                        "[LLM-7] Successfully extracted LLM content | contentLength={}",
                        contentText.length()
                );

                log.info(
                        "[LLM-8] Returning LLM content to AiCommerceAdvisor"
                );

                return contentText;

            } catch (Exception e) {

                log.error(
                        "[LLM-ERROR] Failed to parse LiteLLM response",
                        e
                );

                throw new IllegalStateException(
                        "Unable to parse LLM gateway response",
                        e
                );
            }

        } catch (RestClientResponseException e) {

            log.error(
                    "[LLM-HTTP-ERROR] LiteLLM request failed | status={} | body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );

            throw e;

        } catch (Exception e) {

            log.error(
                    "[LLM-ERROR] Unexpected LiteLLM error",
                    e
            );

            throw e;
        }
    }
}