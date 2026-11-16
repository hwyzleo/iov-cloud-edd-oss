package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 日志对象上传授权结果（EDD-OSS-DSN-CR-003 §8.2）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogObjectUploadResult {

    /**
     * 对象标识
     */
    private String objectId;
    /**
     * 服务端生成对象键
     */
    private String objectKey;
    /**
     * 预签名 PUT 地址，不落库、不写日志
     */
    private String uploadUrl;
    /**
     * 上传时必须原样携带的强制头
     */
    private Map<String, String> headers;
    /**
     * 原授权过期时间
     */
    private Instant expiresAt;
    /**
     * 当前生效单对象上限
     */
    private Long maxSizeBytes;

}
