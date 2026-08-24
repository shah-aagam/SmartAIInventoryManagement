package com.example.ZCHackathon.event;

import com.example.ZCHackathon.suggestion.TriggerReason;

public record InventorySignal(String productId, TriggerReason trigger) {
}
