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
 * 创建替换任务请求（EDD-OSS-DSN-CR-002 §9.4）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageReplacementRequest {

    /**
     * 新图片原始文件名
     */
    @NotBlank(message = "originalFilename must not be blank")
    @Size(max = 255, message = "originalFilename length must not exceed 255")
    private String originalFilename;

    /**
     * 新图片声明 MIME，必须与原图片实际 MIME 一致
     */
    @NotBlank(message = "contentType must not be blank")
    private String contentType;

    /**
     * 新图片声明字节数
     */
    @NotNull(message = "size must not be null")
    @Min(value = 1, message = "size must be greater than 0")
    @Max(value = 10485760, message = "size must not exceed 10485760")
    private Long size;

}
