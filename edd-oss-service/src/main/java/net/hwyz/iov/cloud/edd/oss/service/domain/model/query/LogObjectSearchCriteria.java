package net.hwyz.iov.cloud.edd.oss.service.domain.model.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogBusinessType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.LogUploadState;

import java.time.Instant;

/**
 * 日志对象搜索条件（EDD-OSS-DSN-CR-003 §6.2）。
 * <p>
 * {@code callerService} 必填，由服务端从 X-Caller-Service 注入；排序固定 id DESC。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectSearchCriteria {

    /**
     * 调用方归属（必填）
     */
    private String callerService;
    /**
     * 业务类型（可选）
     */
    private LogBusinessType businessType;
    /**
     * 业务主标识（可选）
     */
    private String businessId;
    /**
     * 上传状态（可选）
     */
    private LogUploadState state;
    /**
     * 授权时间下界（含）
     */
    private Instant beginTime;
    /**
     * 授权时间上界（不含）
     */
    private Instant endTime;

}
