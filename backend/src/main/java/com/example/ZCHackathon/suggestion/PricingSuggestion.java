package com.example.ZCHackathon.suggestion;

import com.example.ZCHackathon.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class PricingSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal recommendedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceDirection direction;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 1600)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerReason triggerReason;

    protected PricingSuggestion() {
    }

    public PricingSuggestion(Product product, BigDecimal recommendedPrice, PriceDirection direction,
                              double confidence, String reasoning, TriggerReason triggerReason) {
        this.product = product;
        this.currentPrice = product.getCurrentPrice();
        this.recommendedPrice = recommendedPrice;
        this.direction = direction;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.triggerReason = triggerReason;
        this.status = SuggestionStatus.PENDING;
    }

    public void decide(SuggestionStatus decision) {
        if (status != SuggestionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING suggestions can be decided");
        }
        if (decision != SuggestionStatus.ACCEPTED && decision != SuggestionStatus.REJECTED) {
            throw new IllegalArgumentException("Decision must be ACCEPTED or REJECTED");
        }
        status = decision;
    }

    public String getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getRecommendedPrice() { return recommendedPrice; }
    public PriceDirection getDirection() { return direction; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
}
