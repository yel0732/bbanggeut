package com.A501.backend.domain.report.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGameResult;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnBean(GameReportQueryPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportGameSnapshotService {

    private final DailyReportRepository dailyReportRepository;
    private final ReportGameResultRepository reportGameResultRepository;
    private final GameReportQueryPort gameReportQueryPort;
    private final ReportGameResultMapper reportGameResultMapper;

    @Transactional
    public int createSnapshots(Long reportId) {
        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() ->
                        new IllegalArgumentException("일일 리포트를 찾을 수 없습니다."));

        List<ReportGameSource> sources =
                gameReportQueryPort.findCompletedGames(
                        report.getChildId(),
                        report.getPeriodStartedAt(),
                        report.getPeriodEndedAt());

        Set<UUID> registeredSessionIds = new HashSet<>();
        reportGameResultRepository.findAllByReportId(reportId)
                .forEach(result ->
                        registeredSessionIds.add(result.getGameSessionId()));

        List<ReportGameResult> snapshots = sources.stream()
                .filter(source ->
                        registeredSessionIds.add(source.gameSessionId()))
                .map(source ->
                        reportGameResultMapper.toSnapshot(reportId, source))
                .toList();

        reportGameResultRepository.saveAll(snapshots);
        return snapshots.size();
    }
}
