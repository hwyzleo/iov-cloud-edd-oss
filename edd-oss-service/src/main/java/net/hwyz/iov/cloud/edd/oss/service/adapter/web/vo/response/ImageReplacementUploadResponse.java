package net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 替换任务上传参数响应（EDD-OSS-DSN-CR-002 §9.4）。
 * <p>
 * {@code objectKey} 为原稳定对象键；{@code uploadUrl} 指向临时对象，二者不得混用。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageReplacementUploadResponse {

    private String replacementId;
    private String objectId;
    private String objectKey;
    private String uploadUrl;
    private String httpMethod;
    private String contentType;
    private Long expiresAt;

}
