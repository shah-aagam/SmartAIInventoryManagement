package com.example.ZCHackathon.suggestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String> {
    List<ReorderSuggestion> findByStatusOrderByIdDesc(SuggestionStatus status);

    boolean existsByProductIdAndTriggerReasonAndStatus(String productId, TriggerReason triggerReason, SuggestionStatus status);
}
