package com.example.ZCHackathon.api;

import com.example.ZCHackathon.product.Category;
import com.example.ZCHackathon.product.Product;
import com.example.ZCHackathon.product.ProductStatus;
import com.example.ZCHackathon.service.StockPulseService;
import com.example.ZCHackathon.suggestion.SuggestionStatus;
import com.example.ZCHackathon.suggestion.TriggerReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class ApiController {
    private final StockPulseService service;

    public ApiController(StockPulseService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public List<Product> products(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Category category) {
        return service.products(status, category);
    }

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody CreateProductRequest request) {
        return service.createProduct(
                request.id(), request.sku(), request.name(), request.category(),
                request.currentPrice(), request.stockLevel(),
                request.reorderThreshold(), request.demandVelocity()
        );
    }

    @PatchMapping("/products/{id}/stock")
    public Product stock(@PathVariable String id, @Valid @RequestBody StockRequest request) {
        return service.stock(id, request.stockLevel());
    }

    @PostMapping("/products/{id}/orders")
    public Product order(@PathVariable String id, @Valid @RequestBody OrderRequest request) {
        return service.order(id, request.quantity());
    }

    @PostMapping("/products/{id}/suggest-pricing")
    public Object suggestPricing(@PathVariable String id) {
        return service.createPricingSuggestion(id, TriggerReason.MANUAL);
    }

    @PostMapping("/products/{id}/suggest-reorder")
    public Object suggestReorder(@PathVariable String id) {
        return service.createReorderSuggestion(id, TriggerReason.MANUAL);
    }

    @GetMapping("/pricing-suggestions")
    public Object pricingSuggestions() {
        return service.pricing();
    }

    @GetMapping("/reorder-suggestions")
    public Object reorderSuggestions() {
        return service.reorder();
    }

    @PatchMapping("/pricing-suggestions/{id}")
    public Object pricingDecision(@PathVariable String id, @Valid @RequestBody DecisionRequest request) {
        return service.pricingDecision(id, request.status());
    }

    @PatchMapping("/reorder-suggestions/{id}")
    public Object reorderDecision(@PathVariable String id, @Valid @RequestBody DecisionRequest request) {
        return service.reorderDecision(id, request.status());
    }

    @GetMapping("/strategy")
    public Map<String, String> strategy() {
        return Map.of("active", service.strategy());
    }

    @PutMapping("/strategy")
    public Map<String, String> strategy(@Valid @RequestBody StrategyRequest request) {
        service.strategy(request.active());
        return Map.of("active", service.strategy());
    }

    public record CreateProductRequest(
            @NotBlank String id,
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull Category category,
            @NotNull @DecimalMin("0.01") BigDecimal currentPrice,
            @Min(0) int stockLevel,
            @Min(0) int reorderThreshold,
            @Min(0) int demandVelocity
    ) {}

    public record StockRequest(@Min(0) int stockLevel) {}

    public record OrderRequest(@Min(1) int quantity) {}

    public record DecisionRequest(@NotNull SuggestionStatus status) {}

    public record StrategyRequest(@NotBlank String active) {}
}
