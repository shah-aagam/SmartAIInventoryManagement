package com.example.ZCHackathon.suggestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String> {
    List<PricingSuggestion> findByStatusOrderByIdDesc(SuggestionStatus status);

    boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason triggerReason, SuggestionStatus status);

    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);
}
