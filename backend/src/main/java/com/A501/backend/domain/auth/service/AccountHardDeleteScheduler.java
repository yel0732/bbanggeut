package com.A501.backend.domain.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 계정의 데이터를 주기적으로 물리 삭제한다.
 *
 * <p><b>{@code @ConditionalOnProperty} 가드가 필수다.</b> 가드 없이 {@code @Scheduled}만 두면
 * 로컬 실행과 {@code @SpringBootTest}에서도 빈이 등록되어 계정이 삭제된다. 되돌릴 수 없는 작업이라
 * 기본값을 false로 두고 운영에서만 켠다({@code DailyReportJobScheduler}와 동일한 패턴).
 *
 * <p>인스턴스가 1대라는 가정 위에서 동작한다. 중복 실행을 막는 락이 없어 다중 인스턴스로 확장하면
 * 같은 계정을 동시에 purge할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "account.purge", name = "enabled", havingValue = "true")
public class AccountHardDeleteScheduler {

    private final AccountHardDeleteService accountHardDeleteService;

    @Scheduled(cron = "${account.purge.cron}", zone = "${account.purge.zone}")
    public void purgeWithdrawnAccounts() {
        // 두 배치를 각각 감싼다 — 계정 파기가 실패해도 자녀 파기는 돌아야 하고, 그 반대도 같다.
        // 여기서 예외가 새어 나가면 스케줄러 스레드가 죽어 이후 주기가 아예 돌지 않는다.
        try {
            accountHardDeleteService.purgeBatch();
        } catch (Exception e) {
            log.error("계정 hard delete 배치 실행 실패", e);
        }
        try {
            accountHardDeleteService.purgeDeletedChildrenBatch();
        } catch (Exception e) {
            log.error("삭제된 자녀 hard delete 배치 실행 실패", e);
        }
    }
}
