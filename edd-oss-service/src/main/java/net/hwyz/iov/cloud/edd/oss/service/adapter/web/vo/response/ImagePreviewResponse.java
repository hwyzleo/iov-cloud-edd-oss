package net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片预览响应（EDD-OSS-DSN-CR-001 §10.2.3）。
 * <p>
 * 不重定向、不代理图片内容，只返回预览信息。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImagePreviewResponse {

    private String objectId;
    private String status;
    private boolean previewable;
    private String previewUrl;

}
