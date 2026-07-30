package com.A501.backend.domain.report.port;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.A501.backend.domain.report.model.ReportDeliverySchedule;

public interface ChildReportQueryPort {

    List<ReportDeliverySchedule> findActiveDeliverySchedules();

    Slice<Long> findActiveChildIds(
            ReportDeliverySchedule schedule,
            Pageable pageable);
}
