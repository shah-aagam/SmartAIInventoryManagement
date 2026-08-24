package com.example.ZCHackathon.service;

import com.example.ZCHackathon.commerce.CommerceAdvisor;
import com.example.ZCHackathon.commerce.CommerceRecommendation;
import com.example.ZCHackathon.event.InventorySignal;
import com.example.ZCHackathon.product.Category;
import com.example.ZCHackathon.product.Product;
import com.example.ZCHackathon.product.ProductRepository;
import com.example.ZCHackathon.product.ProductStatus;
import com.example.ZCHackathon.suggestion.*;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class StockPulseService {
    private final ProductRepository products;
    private final PricingSuggestionRepository pricing;
    private final ReorderSuggestionRepository reorder;
    private final Map<String, CommerceAdvisor> advisors = new HashMap<>();
    private final ApplicationEventPublisher events;
    private volatile String active = "rule";

    public StockPulseService(ProductRepository products,
                             PricingSuggestionRepository pricing,
                             ReorderSuggestionRepository reorder,
                             List<CommerceAdvisor> advisors,
                             ApplicationEventPublisher events) {
        this.products = products;
        this.pricing = pricing;
        this.reorder = reorder;
        this.events = events;
        advisors.forEach(advisor -> this.advisors.put(advisor.key(), advisor));
    }

    public List<Product> products(ProductStatus status, Category category) {
        if (status != null && category != null) {
            return products.findByStatusAndCategory(status, category);
        }
        if (status != null) {
            return products.findByStatus(status);
        }
        if (category != null) {
            return products.findByCategory(category);
        }
        return products.findAll();
    }

    @Transactional
    public Product createProduct(String id, String sku, String name, Category category,
                                 BigDecimal currentPrice, int stockLevel,
                                 int reorderThreshold, int demandVelocity) {
        if (products.existsById(id)) {
            throw new IllegalArgumentException("Product id already exists: " + id);
        }
        if (stockLevel < 0 || reorderThreshold < 0 || demandVelocity < 0) {
            throw new IllegalArgumentException("Stock, threshold and demand velocity cannot be negative");
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("Current price must be positive");
        }

        ProductStatus status = stockLevel == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE;
        Product product = new Product(
                id, sku, name, category, currentPrice,
                stockLevel, reorderThreshold, demandVelocity, status
        );
        return products.save(product);
    }

    public Product get(String id) {
        return products.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
    }

    @Transactional
    public Product stock(String id, int newStockLevel) {
        Product product = get(id);
        product.setStockLevel(newStockLevel);
        signal(product);
        return product;
    }

    @Transactional
    public Product order(String id, int quantity) {
        Product product = get(id);
        product.sell(quantity);
        signal(product);
        return product;
    }

    private double avg(Category category) {
        return products.findByCategory(category)
                .stream()
                .mapToInt(Product::getDemandVelocity)
                .average()
                .orElse(1.0);
    }

    private void signal(Product product) {
        if (product.getStockLevel() < product.getReorderThreshold()) {
            events.publishEvent(new InventorySignal(product.getId(), TriggerReason.INVENTORY_LOW));
        }

        if (product.getDemandVelocity() > avg(product.getCategory()) * 3) {
            events.publishEvent(new InventorySignal(product.getId(), TriggerReason.DEMAND_SPIKE));
        }
    }

    @Transactional
    public void createBoth(String id, TriggerReason trigger) {
        Product product = get(id);
        CommerceRecommendation recommendation = getActiveAdvisor()
                .recommend(product, avg(product.getCategory()), trigger);

        if (!pricing.existsByProductIdAndTriggerReasonAndStatus(
                id, trigger, SuggestionStatus.PENDING)) {
            pricing.save(new PricingSuggestion(
                    product,
                    recommendation.price(),
                    recommendation.direction(),
                    recommendation.pricingConfidence(),
                    recommendation.pricingReasoning(),
                    trigger
            ));
        }

        if (!reorder.existsByProductIdAndTriggerReasonAndStatus(
                id, trigger, SuggestionStatus.PENDING)) {
            reorder.save(new ReorderSuggestion(
                    product,
                    recommendation.quantity(),
                    7,
                    recommendation.reorderConfidence(),
                    recommendation.reorderReasoning(),
                    trigger
            ));
        }

        product.markReviewPending();
    }

    @Transactional
    public PricingSuggestion createPricingSuggestion(String id, TriggerReason trigger) {
        Product product = get(id);
        if (pricing.existsByProductIdAndTriggerReasonAndStatus(
                id, trigger, SuggestionStatus.PENDING)) {
            throw new IllegalStateException("A pending pricing suggestion already exists for this product and trigger");
        }

        CommerceRecommendation recommendation = getActiveAdvisor()
                .recommend(product, avg(product.getCategory()), trigger);

        PricingSuggestion suggestion = new PricingSuggestion(
                product,
                recommendation.price(),
                recommendation.direction(),
                recommendation.pricingConfidence(),
                recommendation.pricingReasoning(),
                trigger
        );
        pricing.save(suggestion);
        product.markReviewPending();
        return suggestion;
    }

    @Transactional
    public ReorderSuggestion createReorderSuggestion(String id, TriggerReason trigger) {
        Product product = get(id);
        if (reorder.existsByProductIdAndTriggerReasonAndStatus(
                id, trigger, SuggestionStatus.PENDING)) {
            throw new IllegalStateException("A pending reorder suggestion already exists for this product and trigger");
        }

        CommerceRecommendation recommendation = getActiveAdvisor()
                .recommend(product, avg(product.getCategory()), trigger);

        ReorderSuggestion suggestion = new ReorderSuggestion(
                product,
                recommendation.quantity(),
                7,
                recommendation.reorderConfidence(),
                recommendation.reorderReasoning(),
                trigger
        );
        reorder.save(suggestion);
        product.markReviewPending();
        return suggestion;
    }

    public List<PricingSuggestion> pricing() {
        return pricing.findByStatusOrderByIdDesc(SuggestionStatus.PENDING);
    }

    public List<ReorderSuggestion> reorder() {
        return reorder.findByStatusOrderByIdDesc(SuggestionStatus.PENDING);
    }

    @Transactional
    public PricingSuggestion pricingDecision(String id, SuggestionStatus decision) {
        PricingSuggestion suggestion = pricing.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pricing suggestion not found: " + id));

        suggestion.decide(decision);
        if (decision == SuggestionStatus.ACCEPTED) {
            suggestion.getProduct().setCurrentPrice(suggestion.getRecommendedPrice());
        }

        syncProductStatus(suggestion.getProduct());
        return suggestion;
    }

    @Transactional
    public ReorderSuggestion reorderDecision(String id, SuggestionStatus decision) {
        ReorderSuggestion suggestion = reorder.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reorder suggestion not found: " + id));

        suggestion.decide(decision);
        if (decision == SuggestionStatus.ACCEPTED) {
            Product product = suggestion.getProduct();
            product.setStockLevel(product.getStockLevel() + suggestion.getRecommendedQuantity());
        }

        syncProductStatus(suggestion.getProduct());
        return suggestion;
    }

    private void syncProductStatus(Product product) {
        if (product.getStockLevel() == 0) {
            product.markReviewPending();
            return;
        }

        boolean pricingPending = pricing.existsByProductIdAndStatus(
                product.getId(), SuggestionStatus.PENDING);

        if (pricingPending) {
            product.markReviewPending();
        } else {
            product.activateIfInStock();
        }
    }

    private CommerceAdvisor getActiveAdvisor() {
        CommerceAdvisor advisor = advisors.get(active);
        if (advisor == null) {
            throw new IllegalStateException("No advisor registered for strategy: " + active);
        }
        return advisor;
    }

    public String strategy() {
        return active;
    }

    public void strategy(String strategy) {
        if (!advisors.containsKey(strategy)) {
            throw new IllegalArgumentException("Strategy must be one of: " + advisors.keySet());
        }
        active = strategy;
    }
}
