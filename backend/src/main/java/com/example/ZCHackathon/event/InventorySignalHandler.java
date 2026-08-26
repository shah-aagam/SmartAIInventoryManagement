package com.example.ZCHackathon.event;

import com.example.ZCHackathon.service.StockPulseService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventorySignalHandler {
    private final StockPulseService service;

    public InventorySignalHandler(StockPulseService service) {
        this.service = service;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(InventorySignal signal) {
        service.createBoth(signal.productId(), signal.trigger());
    }
}
