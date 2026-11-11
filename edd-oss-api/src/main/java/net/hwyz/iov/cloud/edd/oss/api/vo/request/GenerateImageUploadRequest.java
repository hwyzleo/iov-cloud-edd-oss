package net.hwyz.iov.cloud.edd.oss.api.vo.request;

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
 * 生成图片上传任务请求（EDD-OSS-DSN-CR-001 §10.1.1）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageUploadRequest {

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
