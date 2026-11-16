package net.hwyz.iov.cloud.edd.oss.service.adapter.task.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.application.service.LogObjectAppService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 日志对象授权过期扫描任务（EDD-OSS-DSN-CR-003 §9.3）。
 * <p>
 * 仅将到期的 AUTHORIZED 记录条件更新为 EXPIRED；不删除 COS 对象、不删除历史记录、
 * 不改变 UPLOADED 事实。
 *
 * @author hwyz_leo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogObjectExpirationScheduler {

    private final LogObjectAppService logObjectAppService;

    /**
     * 小批量扫描并过期到期记录。
     */
    @Scheduled(fixedDelayString = "${oss.log.expiration-scan-interval-seconds:60}000",
            initialDelayString = "${oss.log.expiration-scan-interval-seconds:60}000")
    public void expireAuthorizedObjects() {
        try {
            logObjectAppService.expireAuthorizedBatch(Instant.now());
        } catch (RuntimeException exception) {
            log.error("日志对象过期扫描失败", exception);
        }
    }

}
