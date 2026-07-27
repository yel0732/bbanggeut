package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.A501.backend.domain.report.event.ReportGenerationRequestedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReportGenerationRequestedEventListener {

    private final ReportGenerationDispatcher dispatcher;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onGenerationRequested(ReportGenerationRequestedEvent event) {
        dispatcher.dispatch(event.reportId());
    }
}
