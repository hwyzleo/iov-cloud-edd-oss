package net.hwyz.iov.cloud.edd.oss.service.application.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 日志对象查询条件（EDD-OSS-DSN-CR-003 §8.6）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectQuery {

    /**
     * 调用方服务名（必填，服务端注入）
     */
    private String callerService;
    /**
     * 业务类型
     */
    private String businessType;
    /**
     * 业务主标识
     */
    private String businessId;
    /**
     * 上传状态
     */
    private String state;
    /**
     * 授权时间下界（含）
     */
    private Instant beginTime;
    /**
     * 授权时间上界（不含）
     */
    private Instant endTime;

}
