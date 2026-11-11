package net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateImageUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler.ImageWebAssembler;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.security.ImageCallerIdentity;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.framework.common.constant.CustomHeaders;
import net.hwyz.iov.cloud.framework.web.controller.BaseController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service Image API（EDD-OSS-DSN-CR-001 §10.1）。
 * <p>
 * 调用方服务名来自 Feign 自动写入的 {@code X-Caller-Service} 请求头（调用方应用名自报），
 * 服务端仅校验格式，不维护调用方名单。
 *
 * @author hwyz_leo
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/service/image/v1")
public class ServiceImageController extends BaseController {

    private final ImageAppService imageAppService;

    @PostMapping("/uploads")
    public ImageUploadResponse generateUpload(@RequestBody @Valid GenerateImageUploadRequest request,
                                              @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false)
                                              String callerServiceNameHeader) {
        String callerServiceName = ImageCallerIdentity.require(callerServiceNameHeader);
        log.info("业务服务[{}]申请图片上传 文件名[{}] MIME[{}] 声明大小[{}]", callerServiceName,
                request.getOriginalFilename(), request.getContentType(), request.getSize());
        GenerateImageUploadCmd cmd = GenerateImageUploadCmd.builder()
                .originalFilename(request.getOriginalFilename())
                .contentType(request.getContentType())
                .size(request.getSize() == null ? 0L : request.getSize())
                .build();
        return ImageWebAssembler.toResponse(imageAppService.generate(cmd, callerServiceName));
    }

    @PostMapping("/uploads/{objectId}/complete")
    public ImageObjectResponse completeUpload(@PathVariable("objectId") String objectId,
                                              @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false)
                                              String callerServiceNameHeader) {
        String callerServiceName = ImageCallerIdentity.require(callerServiceNameHeader);
        log.info("业务服务[{}]确认图片上传 objectId[{}]", callerServiceName, objectId);
        CompleteImageUploadCmd cmd = CompleteImageUploadCmd.builder()
                .objectId(objectId)
                .callerServiceName(callerServiceName)
                .build();
        return ImageWebAssembler.toServiceResponse(imageAppService.complete(cmd));
    }

}
