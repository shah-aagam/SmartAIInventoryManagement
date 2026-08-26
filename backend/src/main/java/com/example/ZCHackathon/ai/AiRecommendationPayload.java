package com.example.ZCHackathon.ai;

import java.math.BigDecimal;

public record AiRecommendationPayload(
        BigDecimal recommendedPrice,
        String direction,
        double pricingConfidence,
        String pricingReasoning,
        int recommendedQuantity,
        double reorderConfidence,
        String reorderReasoning
) {
}
