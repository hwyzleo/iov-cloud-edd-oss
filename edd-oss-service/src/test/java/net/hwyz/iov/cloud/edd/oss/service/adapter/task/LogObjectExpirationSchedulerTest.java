package net.hwyz.iov.cloud.edd.oss.service.adapter.task;

import net.hwyz.iov.cloud.edd.oss.service.adapter.task.scheduler.LogObjectExpirationScheduler;
import net.hwyz.iov.cloud.edd.oss.service.application.service.LogObjectAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志对象过期扫描任务单元测试（EDD-OSS-DSN-CR-003 §9.3）。
 *
 * @author hwyz_leo
 */
class LogObjectExpirationSchedulerTest {

    @Test
    @DisplayName("定时任务调用小批量过期，不抛出异常影响调度")
    void invokesExpirationBatch() {
        LogObjectAppService appService = mock(LogObjectAppService.class);
        when(appService.expireAuthorizedBatch(any(Instant.class))).thenReturn(2);
        LogObjectExpirationScheduler scheduler = new LogObjectExpirationScheduler(appService);
        scheduler.expireAuthorizedObjects();
        verify(appService).expireAuthorizedBatch(any(Instant.class));
    }

    @Test
    @DisplayName("过期扫描异常被捕获，不中断后续调度")
    void swallowsRuntimeException() {
        LogObjectAppService appService = mock(LogObjectAppService.class);
        when(appService.expireAuthorizedBatch(any(Instant.class)))
                .thenThrow(new IllegalStateException("db down"));
        LogObjectExpirationScheduler scheduler = new LogObjectExpirationScheduler(appService);
        assertDoesNotThrow(scheduler::expireAuthorizedObjects);
    }

}
