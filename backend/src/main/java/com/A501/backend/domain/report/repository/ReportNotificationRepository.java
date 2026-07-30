package com.A501.backend.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;

public interface ReportNotificationRepository
        extends JpaRepository<ReportNotification, Long> {

    List<ReportNotification> findAllByReportIdOrderByIdAsc(Long reportId);

    boolean existsByReportIdAndNotificationType(
            Long reportId,
            ReportNotificationType notificationType);

    List<ReportNotification> findByParentIdAndDeliveredAtIsNotNullAndIdGreaterThanOrderByIdAsc(
            Long parentId,
            Long id);

    List<ReportNotification> findTop100ByDeliveredAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            LocalDateTime now);

    List<ReportNotification> findByParentIdAndDeliveredAtIsNull(Long parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ReportNotification notification
            where notification.reportId in :reportIds
               or notification.parentId = :parentId
            """)
    int deleteByReportIdsOrParentId(
            @Param("reportIds") Collection<Long> reportIds,
            @Param("parentId") Long parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ReportNotification notification
            where notification.parentId = :parentId
            """)
    int deleteByParentId(@Param("parentId") Long parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReportNotification notification where notification.childId in :childIds")
    int deleteByChildIds(@Param("childIds") Collection<Long> childIds);
}
