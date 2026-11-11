package net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.mpt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler.ImageWebAssembler;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.ImageObjectQueryRequest;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response.ImagePreviewResponse;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.ImageObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.framework.audit.annotation.Log;
import net.hwyz.iov.cloud.framework.audit.enums.BusinessType;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.bean.PageResult;
import net.hwyz.iov.cloud.framework.security.annotation.RequiresPermissions;
import net.hwyz.iov.cloud.framework.security.util.SecurityUtils;
import net.hwyz.iov.cloud.framework.web.context.SecurityContextHolder;
import net.hwyz.iov.cloud.framework.web.controller.BaseController;
import net.hwyz.iov.cloud.framework.web.util.PageUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * MPT 图片对象管理接口（EDD-OSS-DSN-CR-001 §10.2）。
 *
 * @author hwyz_leo
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mpt/image/v1/objects")
public class MptImageController extends BaseController {

    private final ImageAppService imageAppService;

    /**
     * 数据库分页查询图片对象。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @RequiresPermissions("oss:image:list")
    @GetMapping("/list")
    public ApiResponse<PageResult<ImageObjectResponse>> list(ImageObjectQueryRequest request) {
        log.info("管理后台用户[{}]分页查询图片对象", SecurityContextHolder.getUserName());
        startPage();
        ImageObjectQuery query = ImageObjectQuery.builder()
                .serviceName(request.getServiceName())
                .status(request.getStatus())
                .contentType(request.getContentType())
                .objectKey(request.getObjectKey())
                .originalFilename(request.getOriginalFilename())
                .uploadBeginTime(toInstant(request.getUploadBeginTime()))
                .uploadEndTime(toInstant(request.getUploadEndTime()))
                .build();
        List<ImageObjectResult> results = imageAppService.search(query);
        return ApiResponse.ok(getPageResult(PageUtil.convert(results, ImageWebAssembler::toResponse)));
    }

    /**
     * 查询图片对象完整详情。
     *
     * @param objectId 对象标识
     * @return 图片对象
     */
    @RequiresPermissions("oss:image:query")
    @GetMapping("/{objectId}")
    public ApiResponse<ImageObjectResponse> detail(@PathVariable("objectId") String objectId) {
        log.info("管理后台用户[{}]查询图片对象[{}]", SecurityContextHolder.getUserName(), objectId);
        return ApiResponse.ok(ImageWebAssembler.toResponse(imageAppService.detail(objectId)));
    }

    /**
     * 获取图片预览信息。
     *
     * @param objectId 对象标识
     * @return 预览信息
     */
    @RequiresPermissions("oss:image:preview")
    @GetMapping("/{objectId}/preview")
    public ApiResponse<ImagePreviewResponse> preview(@PathVariable("objectId") String objectId) {
        log.info("管理后台用户[{}]预览图片对象[{}]", SecurityContextHolder.getUserName(), objectId);
        return ApiResponse.ok(ImageWebAssembler.toPreviewResponse(imageAppService.detail(objectId)));
    }

    /**
     * 删除已上传的图片对象。
     *
     * @param objectId 对象标识
     * @return 结果
     */
    @Log(title = "图片对象管理", businessType = BusinessType.DELETE)
    @RequiresPermissions("oss:image:remove")
    @DeleteMapping("/{objectId}")
    public ApiResponse<Void> remove(@PathVariable("objectId") String objectId) {
        Long operatorId = SecurityUtils.getUserId();
        log.info("管理后台用户[{}]删除图片对象[{}]", SecurityContextHolder.getUserName(), objectId);
        imageAppService.delete(objectId, operatorId == null ? "unknown" : operatorId.toString());
        return ApiResponse.ok();
    }

    private Instant toInstant(Long epochMilli) {
        return epochMilli == null ? null : Instant.ofEpochMilli(epochMilli);
    }

}
