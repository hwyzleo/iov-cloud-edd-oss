package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请日志对象上传命令（EDD-OSS-DSN-CR-003 §8.2）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateLogObjectUploadCmd {

    /**
     * 业务类型，本期仅 OTA_UPGRADE_LOG
     */
    private String businessType;
    /**
     * 业务主标识，本期 vehicleTaskId
     */
    private String businessId;
    /**
     * 业务子标识，本期可选 executionId
     */
    private String businessSubId;
    /**
     * 调用方幂等键
     */
    private String requestId;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * 声明 Content-Type
     */
    private String contentType;
    /**
     * 声明字节数
     */
    private Long sizeBytes;
    /**
     * 声明 SHA-256（64 位小写十六进制）
     */
    private String sha256Hex;
    /**
     * 隐私分级元数据
     */
    private String privacyLevel;
    /**
     * 上传授权 TTL 秒数，可空
     */
    private Integer ttlSeconds;

}
