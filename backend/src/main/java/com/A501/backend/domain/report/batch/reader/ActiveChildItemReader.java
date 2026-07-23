package com.A501.backend.domain.report.batch.reader;

import java.util.Collections;
import java.util.Iterator;

import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.A501.backend.domain.report.port.ChildReportQueryPort;

public class ActiveChildItemReader implements ItemReader<Long> {

    private static final int PAGE_SIZE = 500;

    private final ChildReportQueryPort childReportQueryPort;

    private Pageable pageable;
    private Iterator<Long> currentItems;
    private boolean lastPageLoaded;

    public ActiveChildItemReader(ChildReportQueryPort childReportQueryPort) {
        this.childReportQueryPort = childReportQueryPort;
        this.pageable = PageRequest.of(0, PAGE_SIZE);
        this.currentItems = Collections.emptyIterator();
        this.lastPageLoaded = false;
    }

    @Override
    public Long read() {
        while (!currentItems.hasNext()) {
            if (lastPageLoaded) {
                return null;
            }

            Slice<Long> slice = childReportQueryPort.findActiveChildIds(pageable);
            currentItems = slice.getContent().iterator();

            if (slice.hasNext()) {
                pageable = slice.nextPageable();
            } else {
                lastPageLoaded = true;
            }
        }

        return currentItems.next();
    }
}
