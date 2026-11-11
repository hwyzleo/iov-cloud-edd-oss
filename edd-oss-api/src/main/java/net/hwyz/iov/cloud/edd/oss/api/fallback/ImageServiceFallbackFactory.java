package net.hwyz.iov.cloud.edd.oss.api.fallback;

import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.service.ImageService;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateImageUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageUploadResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 图片服务降级工厂。
 * <p>
 * 必须 fail-closed：不得返回伪造的 objectId、uploadUrl 或成功状态（EDD-OSS-DSN-CR-001 §10.1.2）。
 *
 * @author hwyz_leo
 */
@Slf4j
@Component
public class ImageServiceFallbackFactory implements FallbackFactory<ImageService> {

    @Override
    public ImageService create(Throwable cause) {
        log.error("[OSS] 图片服务调用失败，进入降级", cause);
        return new ImageService() {
            @Override
            public ImageUploadResponse generateUpload(GenerateImageUploadRequest request) {
                throw new IllegalStateException("图片上传服务不可用，无法创建上传任务", cause);
            }

            @Override
            public ImageObjectResponse completeUpload(String objectId) {
                throw new IllegalStateException("图片上传服务不可用，无法确认上传任务[" + objectId + "]", cause);
            }
        };
    }

}
