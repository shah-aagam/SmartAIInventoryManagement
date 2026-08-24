package com.example.ZCHackathon.commerce;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.ZCHackathon.ai.AiRecommendationPayload;
import com.example.ZCHackathon.ai.LLMGateway;
import com.example.ZCHackathon.product.Product;
import com.example.ZCHackathon.suggestion.PriceDirection;
import com.example.ZCHackathon.suggestion.TriggerReason;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiCommerceAdvisor implements CommerceAdvisor {

    private static final Logger log =
            LoggerFactory.getLogger(AiCommerceAdvisor.class);

    private final LLMGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final RuleBasedCommerceAdvisor fallback;

    public AiCommerceAdvisor(
            LLMGateway llmGateway,
            ObjectMapper objectMapper,
            RuleBasedCommerceAdvisor fallback) {

        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    public String key() {
        return "ai";
    }

    @Override
    public CommerceRecommendation recommend(
            Product p,
            double avg,
            TriggerReason trigger) {

        log.info(
                "[AI-1] AiCommerceAdvisor invoked | product={} | trigger={} | price={} | stock={} | demand={}",
                p.getId(),
                trigger,
                p.getCurrentPrice(),
                p.getStockLevel(),
                p.getDemandVelocity()
        );

        try {

            // 1. Build prompt
            log.info(
                    "[AI-2] Building LLM prompt | product={}",
                    p.getId()
            );

            String prompt = buildPrompt(p, avg, trigger);

            log.info(
                    "[AI-3] LLM prompt built | product={} | promptLength={}",
                    p.getId(),
                    prompt.length()
            );

            // 2. Call LLM
            log.info(
                    "[AI-4] Calling LLM Gateway | product={}",
                    p.getId()
            );

            String raw = llmGateway.callLLM(prompt);

            log.info(
                    "[AI-5] LLM Gateway returned | product={} | responseLength={}",
                    p.getId(),
                    raw != null ? raw.length() : 0
            );

            // 3. Parse
            log.info(
                    "[AI-6] Parsing LLM response | product={}",
                    p.getId()
            );

            AiRecommendationPayload ai = parse(raw);

            log.info(
                    "[AI-7] LLM response parsed | product={} | direction={} | price={} | quantity={}",
                    p.getId(),
                    ai.direction(),
                    ai.recommendedPrice(),
                    ai.recommendedQuantity()
            );

            // 4. Validate
            log.info(
                    "[AI-8] Validating AI recommendation | product={}",
                    p.getId()
            );

            validate(ai, p);

            log.info(
                    "[AI-9] AI recommendation validated | product={}",
                    p.getId()
            );

            // 5. Convert
            CommerceRecommendation recommendation =
                    toRecommendation(ai, p);

            log.info(
                    "[AI-10] AI recommendation created | product={} | price={} | direction={} | quantity={}",
                    p.getId(),
                    recommendation.price(),
                    recommendation.direction(),
                    recommendation.quantity()
            );

            return recommendation;

        } catch (Exception ex) {

            log.error(
                    "[AI-ERROR] AI recommendation failed | product={} | trigger={} | error={}",
                    p.getId(),
                    trigger,
                    ex.getMessage(),
                    ex
            );

            log.warn(
                    "[AI-FALLBACK] Switching to RuleBasedCommerceAdvisor | product={}",
                    p.getId()
            );

            CommerceRecommendation fallbackRecommendation =
                    fallback.recommend(p, avg, trigger);

            log.info(
                    "[AI-FALLBACK] Rule recommendation generated | product={} | price={} | direction={} | quantity={}",
                    p.getId(),
                    fallbackRecommendation.price(),
                    fallbackRecommendation.direction(),
                    fallbackRecommendation.quantity()
            );

            return fallbackRecommendation;
        }
    }

    private String buildPrompt(
            Product p,
            double avg,
            TriggerReason trigger) {

        String situation = switch (trigger) {

            case INVENTORY_LOW ->
                    "Inventory is below the reorder threshold. Decide whether a modest price increase protects scarce inventory or whether another pricing action is more appropriate. Also recommend replenishment quantity.";

            case DEMAND_SPIKE ->
                    "Demand velocity is significantly above the category average. Decide whether a modest price increase can capture demand without overreacting. Also recommend replenishment quantity.";

            case MANUAL ->
                    "A merchandiser manually requested a recommendation. Evaluate the current inventory and demand context and provide a balanced pricing and replenishment recommendation.";

            case INITIAL ->
                    "This is an initial recommendation. Evaluate the current inventory and demand context and provide a balanced pricing and replenishment recommendation.";
        };

        String prompt = """

                You are the commerce advisor for an online retailer.

                Return ONLY valid JSON. Do not use markdown or code fences.

                Trigger context:

                %s

                Product:

                - name: %s
                - category: %s
                - currentPrice: %s
                - stockLevel: %d
                - reorderThreshold: %d
                - demandVelocityLast24h: %d
                - categoryAverageDemandVelocity: %.2f

                Business constraints:

                - recommendedPrice must be positive.
                - Keep recommendedPrice within 0.5x to 2.0x of currentPrice.
                - direction must be INCREASE, DECREASE, or HOLD.
                - If direction is HOLD, recommendedPrice must equal currentPrice.
                - recommendedQuantity must be a positive integer.
                - confidence values must be between 0 and 1.
                - Give concise reasoning a merchandiser can act on.

                Required JSON shape:

                {
                  "recommendedPrice": 29.99,
                  "direction": "INCREASE",
                  "pricingConfidence": 0.82,
                  "pricingReasoning": "...",
                  "recommendedQuantity": 40,
                  "reorderConfidence": 0.78,
                  "reorderReasoning": "..."
                }

                """.formatted(
                situation,
                p.getName(),
                p.getCategory(),
                p.getCurrentPrice(),
                p.getStockLevel(),
                p.getReorderThreshold(),
                p.getDemandVelocity(),
                avg
        );

        return prompt;
    }

    private AiRecommendationPayload parse(String raw) throws Exception {

        String json = raw.trim();

        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "");
            json = json.replaceFirst("\\s*```$", "");
        }

        return objectMapper.readValue(
                json,
                AiRecommendationPayload.class
        );
    }

    private void validate(
            AiRecommendationPayload ai,
            Product p) {

        if (ai.recommendedPrice() == null
                || ai.recommendedPrice().signum() <= 0) {

            throw new IllegalArgumentException(
                    "AI returned an invalid price"
            );
        }

        BigDecimal min =
                p.getCurrentPrice()
                        .multiply(BigDecimal.valueOf(0.5));

        BigDecimal max =
                p.getCurrentPrice()
                        .multiply(BigDecimal.valueOf(2.0));

        if (ai.recommendedPrice().compareTo(min) < 0
                || ai.recommendedPrice().compareTo(max) > 0) {

            throw new IllegalArgumentException(
                    "AI price is outside safe bounds"
            );
        }

        PriceDirection direction;

        try {
            direction =
                    PriceDirection.valueOf(
                            ai.direction().toUpperCase()
                    );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "AI returned an invalid price direction"
            );
        }

        int comparison =
                ai.recommendedPrice()
                        .compareTo(p.getCurrentPrice());

        if ((direction == PriceDirection.INCREASE
                && comparison <= 0)
                || (direction == PriceDirection.DECREASE
                && comparison >= 0)
                || (direction == PriceDirection.HOLD
                && comparison != 0)) {

            throw new IllegalArgumentException(
                    "AI direction does not match recommended price"
            );
        }

        if (ai.recommendedQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "AI returned an invalid reorder quantity"
            );
        }

        if (!betweenZeroAndOne(ai.pricingConfidence())
                || !betweenZeroAndOne(ai.reorderConfidence())) {

            throw new IllegalArgumentException(
                    "AI confidence must be between 0 and 1"
            );
        }

        if (ai.pricingReasoning() == null
                || ai.pricingReasoning().isBlank()
                || ai.reorderReasoning() == null
                || ai.reorderReasoning().isBlank()) {

            throw new IllegalArgumentException(
                    "AI reasoning must not be empty"
            );
        }
    }

    private boolean betweenZeroAndOne(double value) {

        return !Double.isNaN(value)
                && value >= 0.0
                && value <= 1.0;
    }

    private CommerceRecommendation toRecommendation(
            AiRecommendationPayload ai,
            Product p) {

        PriceDirection direction =
                PriceDirection.valueOf(
                        ai.direction().toUpperCase()
                );

        BigDecimal price =
                ai.recommendedPrice()
                        .setScale(2, RoundingMode.HALF_UP);

        return new CommerceRecommendation(
                price,
                direction,
                ai.pricingConfidence(),
                ai.pricingReasoning(),
                ai.recommendedQuantity(),
                ai.reorderConfidence(),
                ai.reorderReasoning()
        );
    }
}