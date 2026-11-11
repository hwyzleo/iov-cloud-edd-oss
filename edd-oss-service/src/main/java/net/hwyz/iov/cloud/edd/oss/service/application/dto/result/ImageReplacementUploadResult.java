package net.hwyz.iov.cloud.edd.oss.service.application.dto.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 替换任务上传参数结果（EDD-OSS-DSN-CR-002 §9.4）。
 * <p>
 * {@code objectKey} 为原稳定对象键；{@code uploadUrl} 指向临时对象，二者不得混用。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageReplacementUploadResult {

    private String replacementId;
    private String objectId;
    private String objectKey;
    private String uploadUrl;
    private String httpMethod;
    private String contentType;
    private Instant expiresAt;

}
