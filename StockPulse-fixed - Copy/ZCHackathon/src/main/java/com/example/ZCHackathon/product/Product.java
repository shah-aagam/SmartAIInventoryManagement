package com.example.ZCHackathon.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private int stockLevel;

    @Column(nullable = false)
    private int reorderThreshold;

    @Column(nullable = false)
    private int demandVelocity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    protected Product() {
    }

    public Product(String id, String sku, String name, Category category, BigDecimal price,
                   int stock, int threshold, int velocity, ProductStatus status) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.currentPrice = price;
        this.stockLevel = stock;
        this.reorderThreshold = threshold;
        this.demandVelocity = velocity;
        this.status = status;
    }

    public void setStockLevel(int stockLevel) {
        if (stockLevel < 0) {
            throw new IllegalArgumentException("Stock level cannot be negative");
        }
        this.stockLevel = stockLevel;
        if (stockLevel == 0) {
            status = ProductStatus.OUT_OF_STOCK;
        }
    }

    public void sell(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive");
        }
        if (quantity > stockLevel) {
            throw new IllegalArgumentException("Order quantity exceeds available stock");
        }
        setStockLevel(stockLevel - quantity);
        demandVelocity += quantity;
    }

    public void setCurrentPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        currentPrice = price;
    }

    public void markReviewPending() {
        if (stockLevel > 0) {
            status = ProductStatus.PRICE_REVIEW_PENDING;
        } else {
            status = ProductStatus.OUT_OF_STOCK;
        }
    }

    public void activateIfInStock() {
        if (stockLevel > 0) {
            status = ProductStatus.ACTIVE;
        } else {
            status = ProductStatus.OUT_OF_STOCK;
        }
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public int getStockLevel() { return stockLevel; }
    public int getReorderThreshold() { return reorderThreshold; }
    public int getDemandVelocity() { return demandVelocity; }
    public ProductStatus getStatus() { return status; }
}
