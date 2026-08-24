package com.example.ZCHackathon.suggestion;

import com.example.ZCHackathon.product.Product;
import jakarta.persistence.*;

@Entity
public class ReorderSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    private Product product;

    @Column(nullable = false)
    private int currentStock;

    @Column(nullable = false)
    private int recommendedQuantity;

    @Column(nullable = false)
    private int suggestedLeadTimeDays;

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

    protected ReorderSuggestion() {
    }

    public ReorderSuggestion(Product product, int quantity, int leadTimeDays,
                              double confidence, String reasoning, TriggerReason triggerReason) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Recommended quantity must be positive");
        }
        this.product = product;
        this.currentStock = product.getStockLevel();
        this.recommendedQuantity = quantity;
        this.suggestedLeadTimeDays = leadTimeDays;
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
    public int getCurrentStock() { return currentStock; }
    public int getRecommendedQuantity() { return recommendedQuantity; }
    public int getSuggestedLeadTimeDays() { return suggestedLeadTimeDays; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
}
