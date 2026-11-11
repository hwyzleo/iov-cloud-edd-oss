package net.hwyz.iov.cloud.edd.oss.api.service;

import net.hwyz.iov.cloud.edd.oss.api.fallback.ImageServiceFallbackFactory;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateImageUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageUploadResponse;
import net.hwyz.iov.cloud.framework.common.constant.ServiceNameConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 图片服务接口（EDD-OSS-DSN-CR-001 §10.1）。
 * <p>
 * 仅允许持有 CIAM Client Credentials Token 的内部业务服务调用；端上不得直接访问。
 *
 * @author hwyz_leo
 */
@FeignClient(contextId = "imageService", value = ServiceNameConstants.EDD_OSS, path = "/api/service/image/v1",
        fallbackFactory = ImageServiceFallbackFactory.class)
public interface ImageService {

    /**
     * 创建图片上传任务并返回 COS PUT 预签名参数。
     *
     * @param request 上传申请
     * @return 上传参数
     */
    @PostMapping("/uploads")
    ImageUploadResponse generateUpload(@RequestBody GenerateImageUploadRequest request);

    /**
     * 完成图片上传确认。
     *
     * @param objectId 上传任务标识
     * @return 已确认的图片对象信息
     */
    @PostMapping("/uploads/{objectId}/complete")
    ImageObjectResponse completeUpload(@PathVariable("objectId") String objectId);

}
