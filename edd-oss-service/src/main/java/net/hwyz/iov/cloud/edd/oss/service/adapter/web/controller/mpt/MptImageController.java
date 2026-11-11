package net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.mpt;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.ImageUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler.ImageWebAssembler;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.GenerateDefaultAssetRequest;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.GenerateImageReplacementRequest;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.ImageObjectQueryRequest;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.UpdateDefaultAssetRequest;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response.ImagePreviewResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.response.ImageReplacementUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageReplacementCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.UpdateDefaultAssetCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.ImageObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.DefaultAssetAppService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * MPT 图片对象管理接口（EDD-OSS-DSN-CR-001 §10.2、EDD-OSS-DSN-CR-002 §10.3）。
 *
 * @author hwyz_leo
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mpt/image/v1/objects")
public class MptImageController extends BaseController {

    private final ImageAppService imageAppService;
    private final DefaultAssetAppService defaultAssetAppService;

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
        log.info("管理后台用户[{}]删除图片对象[{}]", SecurityContextHolder.getUserName(), objectId);
        imageAppService.delete(objectId, currentOperator());
        return ApiResponse.ok();
    }

    /**
     * 申请默认素材上传（EDD-OSS-DSN-CR-002 §10.3.1）。
     *
     * @param request 申请请求
     * @return 上传参数
     */
    @Log(title = "默认素材管理", businessType = BusinessType.INSERT)
    @RequiresPermissions("oss:image:upload")
    @PostMapping("/uploads")
    public ApiResponse<ImageUploadResponse> generateDefaultAsset(@RequestBody @Valid GenerateDefaultAssetRequest request) {
        String operator = currentOperator();
        log.info("管理后台用户[{}]申请默认素材上传 素材名称[{}] 文件名[{}]", SecurityContextHolder.getUserName(),
                request.getAssetName(), request.getOriginalFilename());
        GenerateDefaultAssetCmd cmd = GenerateDefaultAssetCmd.builder()
                .assetName(request.getAssetName())
                .description(request.getDescription())
                .originalFilename(request.getOriginalFilename())
                .contentType(request.getContentType())
                .size(request.getSize() == null ? 0L : request.getSize())
                .build();
        return ApiResponse.ok(ImageWebAssembler.toResponse(defaultAssetAppService.generate(cmd, operator)));
    }

    /**
     * 完成默认素材上传（EDD-OSS-DSN-CR-002 §10.3.2）。
     *
     * @param objectId 对象标识
     * @return 图片对象
     */
    @Log(title = "默认素材管理", businessType = BusinessType.UPDATE)
    @RequiresPermissions("oss:image:upload")
    @PostMapping("/uploads/{objectId}/complete")
    public ApiResponse<ImageObjectResponse> completeDefaultAsset(@PathVariable("objectId") String objectId) {
        String operator = currentOperator();
        log.info("管理后台用户[{}]确认默认素材上传 objectId[{}]", SecurityContextHolder.getUserName(), objectId);
        return ApiResponse.ok(ImageWebAssembler.toResponse(defaultAssetAppService.complete(objectId, operator)));
    }

    /**
     * 编辑默认素材名称与备注（EDD-OSS-DSN-CR-002 §10.3.3）。
     *
     * @param objectId 对象标识
     * @param request  编辑请求
     * @return 图片对象
     */
    @Log(title = "默认素材管理", businessType = BusinessType.UPDATE)
    @RequiresPermissions("oss:image:edit")
    @PutMapping("/{objectId}/asset")
    public ApiResponse<ImageObjectResponse> updateAsset(@PathVariable("objectId") String objectId,
                                                        @RequestBody @Valid UpdateDefaultAssetRequest request) {
        String operator = currentOperator();
        log.info("管理后台用户[{}]编辑默认素材 objectId[{}]", SecurityContextHolder.getUserName(), objectId);
        UpdateDefaultAssetCmd cmd = UpdateDefaultAssetCmd.builder()
                .objectId(objectId)
                .assetName(request.getAssetName())
                .description(request.getDescription())
                .build();
        return ApiResponse.ok(ImageWebAssembler.toResponse(defaultAssetAppService.updateAsset(cmd, operator)));
    }

    /**
     * 创建替换任务（EDD-OSS-DSN-CR-002 §10.3.4）。
     *
     * @param objectId 对象标识
     * @param request  替换申请请求
     * @return 替换上传参数
     */
    @Log(title = "默认素材管理", businessType = BusinessType.INSERT)
    @RequiresPermissions("oss:image:replace")
    @PostMapping("/{objectId}/replacements")
    public ApiResponse<ImageReplacementUploadResponse> generateReplacement(
            @PathVariable("objectId") String objectId,
            @RequestBody @Valid GenerateImageReplacementRequest request) {
        String operator = currentOperator();
        log.info("管理后台用户[{}]创建替换任务 objectId[{}] 文件名[{}]", SecurityContextHolder.getUserName(), objectId,
                request.getOriginalFilename());
        GenerateImageReplacementCmd cmd = GenerateImageReplacementCmd.builder()
                .objectId(objectId)
                .originalFilename(request.getOriginalFilename())
                .contentType(request.getContentType())
                .size(request.getSize() == null ? 0L : request.getSize())
                .build();
        return ApiResponse.ok(ImageWebAssembler.toResponse(
                defaultAssetAppService.generateReplacement(cmd, operator)));
    }

    /**
     * 完成或恢复替换（EDD-OSS-DSN-CR-002 §10.3.5）。
     *
     * @param objectId      对象标识
     * @param replacementId 替换任务标识
     * @return 更新后的图片对象
     */
    @Log(title = "默认素材管理", businessType = BusinessType.UPDATE)
    @RequiresPermissions("oss:image:replace")
    @PostMapping("/{objectId}/replacements/{replacementId}/complete")
    public ApiResponse<ImageObjectResponse> completeReplacement(@PathVariable("objectId") String objectId,
                                                                @PathVariable("replacementId") String replacementId) {
        String operator = currentOperator();
        log.info("管理后台用户[{}]完成替换 objectId[{}] replacementId[{}]", SecurityContextHolder.getUserName(),
                objectId, replacementId);
        CompleteImageReplacementCmd cmd = CompleteImageReplacementCmd.builder()
                .objectId(objectId)
                .replacementId(replacementId)
                .operator(operator)
                .build();
        return ApiResponse.ok(ImageWebAssembler.toResponse(defaultAssetAppService.completeReplacement(cmd)));
    }

    private String currentOperator() {
        Long operatorId = SecurityUtils.getUserId();
        return operatorId == null ? "unknown" : operatorId.toString();
    }

    private Instant toInstant(Long epochMilli) {
        return epochMilli == null ? null : Instant.ofEpochMilli(epochMilli);
    }

}
