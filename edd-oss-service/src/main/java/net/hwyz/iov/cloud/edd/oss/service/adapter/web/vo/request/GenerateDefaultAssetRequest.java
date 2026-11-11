package net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请默认素材上传请求（EDD-OSS-DSN-CR-002 §9.1）。
 * <p>
 * 不接收 serviceName：后台上传由服务端固定归属 {@code edd-oss}。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDefaultAssetRequest {

    /**
     * 素材名称，去首尾空白后 1～100 字符
     */
    @NotBlank(message = "assetName must not be blank")
    @Size(max = 100, message = "assetName length must not exceed 100")
    private String assetName;

    /**
     * 备注，最大 255 字符
     */
    @Size(max = 255, message = "description length must not exceed 255")
    private String description;

    /**
     * 原始文件名，仅作元数据展示，不进入对象键
     */
    @NotBlank(message = "originalFilename must not be blank")
    @Size(max = 255, message = "originalFilename length must not exceed 255")
    private String originalFilename;

    /**
     * 声明 MIME，仅支持 image/jpeg、image/png、image/webp
     */
    @NotBlank(message = "contentType must not be blank")
    private String contentType;

    /**
     * 声明字节数，0 &lt; size &lt;= 10485760
     */
    @NotNull(message = "size must not be null")
    @Min(value = 1, message = "size must be greater than 0")
    @Max(value = 10485760, message = "size must not exceed 10485760")
    private Long size;

}
