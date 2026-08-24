package com.example.ZCHackathon.commerce;

import java.math.BigDecimal;

import com.example.ZCHackathon.suggestion.PriceDirection;

public record CommerceRecommendation(BigDecimal price, PriceDirection direction, double pricingConfidence, String pricingReasoning, int quantity, double reorderConfidence, String reorderReasoning) {

}
