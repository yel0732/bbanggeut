package com.A501.backend.domain.report.port;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ChildReportQueryPort {

    Slice<Long> findActiveChildIds(Pageable pageable);
}
