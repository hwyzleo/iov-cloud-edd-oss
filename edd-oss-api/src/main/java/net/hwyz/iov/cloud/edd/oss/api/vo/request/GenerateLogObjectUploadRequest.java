package net.hwyz.iov.cloud.edd.oss.api.vo.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请日志对象上传授权请求（EDD-OSS-DSN-CR-003 §8.2）。
 * <p>
 * 不接收 callerService、objectKey、objectId；归属与对象键由服务端生成。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateLogObjectUploadRequest {

    /**
     * 业务类型，本期仅 OTA_UPGRADE_LOG
     */
    @NotBlank(message = "businessType must not be blank")
    private String businessType;

    /**
     * 业务主标识，本期 vehicleTaskId；1～128 字符
     */
    @NotBlank(message = "businessId must not be blank")
    @Size(max = 128, message = "businessId length must not exceed 128")
    private String businessId;

    /**
     * 业务子标识，本期可选 executionId；非空时 1～128 字符
     */
    @Size(max = 128, message = "businessSubId length must not exceed 128")
    private String businessSubId;

    /**
     * 调用方幂等键；1～128 字符
     */
    @NotBlank(message = "requestId must not be blank")
    @Size(max = 128, message = "requestId length must not exceed 128")
    private String requestId;

    /**
     * 原始文件名；1～255 字符，不进入对象键
     */
    @NotBlank(message = "fileName must not be blank")
    @Size(max = 255, message = "fileName length must not exceed 255")
    private String fileName;

    /**
     * 声明 Content-Type，仅接受日志 MIME 白名单
     */
    @NotBlank(message = "contentType must not be blank")
    private String contentType;

    /**
     * 声明字节数，大于 0 且不超过服务端配置上限
     */
    @NotNull(message = "sizeBytes must not be null")
    @Min(value = 1, message = "sizeBytes must be greater than 0")
    private Long sizeBytes;

    /**
     * 声明 SHA-256，64 位小写十六进制
     */
    @NotBlank(message = "sha256Hex must not be blank")
    @Pattern(regexp = "[0-9a-f]{64}", message = "sha256Hex must be 64 lowercase hexadecimal characters")
    private String sha256Hex;

    /**
     * 隐私分级元数据；1～32 字符
     */
    @NotBlank(message = "privacyLevel must not be blank")
    @Size(max = 32, message = "privacyLevel length must not exceed 32")
    private String privacyLevel;

    /**
     * 上传授权 TTL 秒数；缺省 900，最大由服务端配置
     */
    @Min(value = 1, message = "ttlSeconds must be greater than 0")
    private Integer ttlSeconds;

}
