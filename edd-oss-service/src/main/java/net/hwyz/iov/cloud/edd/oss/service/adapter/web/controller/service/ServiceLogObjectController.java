package net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.service;

import com.github.pagehelper.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateLogObjectUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectDownloadUrlResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectPageResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectUploadResponse;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.assembler.LogObjectWebAssembler;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.security.LogCallerIdentity;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.LogObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.LogObjectAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.framework.common.bean.PageResult;
import net.hwyz.iov.cloud.framework.common.constant.CustomHeaders;
import net.hwyz.iov.cloud.framework.web.controller.BaseController;
import net.hwyz.iov.cloud.framework.web.util.PageUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Service LogObject API（EDD-OSS-DSN-CR-003 §8）。
 * <p>
 * 所有端点要求 {@code X-Caller-Service}；分页使用数据库物理分页并强制调用方隔离。
 *
 * @author hwyz_leo
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/service/logobject/v1")
public class ServiceLogObjectController extends BaseController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final LogObjectAppService logObjectAppService;

    /**
     * 申请日志对象上传授权。
     *
     * @param request  上传申请
     * @param header   调用方服务名请求头
     * @return 上传参数
     */
    @PostMapping("/uploads")
    public LogObjectUploadResponse generateUpload(
            @RequestBody @Valid GenerateLogObjectUploadRequest request,
            @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false) String header) {
        String callerService = LogCallerIdentity.require(header);
        log.info("业务服务[{}]申请日志上传 businessType[{}] businessId[{}] 声明大小[{}]", callerService,
                request.getBusinessType(), request.getBusinessId(), request.getSizeBytes());
        GenerateLogObjectUploadCmd cmd = GenerateLogObjectUploadCmd.builder()
                .businessType(request.getBusinessType())
                .businessId(request.getBusinessId())
                .businessSubId(request.getBusinessSubId())
                .requestId(request.getRequestId())
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .sizeBytes(request.getSizeBytes())
                .sha256Hex(request.getSha256Hex())
                .privacyLevel(request.getPrivacyLevel())
                .ttlSeconds(request.getTtlSeconds())
                .build();
        return LogObjectWebAssembler.toResponse(logObjectAppService.generate(cmd, callerService));
    }

    /**
     * 查询日志对象登记事实。
     *
     * @param objectId 对象标识
     * @param header   调用方服务名请求头
     * @return 登记事实
     */
    @GetMapping("/objects/{objectId}")
    public LogObjectResponse getObject(@PathVariable("objectId") String objectId,
                                       @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false)
                                       String header) {
        String callerService = LogCallerIdentity.require(header);
        log.info("业务服务[{}]查询日志对象[{}]", callerService, objectId);
        return LogObjectWebAssembler.toResponse(logObjectAppService.detail(objectId, callerService));
    }

    /**
     * 完成日志对象上传核验。
     *
     * @param objectId 对象标识
     * @param header   调用方服务名请求头
     * @return 已核验的登记事实
     */
    @PostMapping("/objects/{objectId}/complete")
    public LogObjectResponse completeUpload(@PathVariable("objectId") String objectId,
                                            @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false)
                                            String header) {
        String callerService = LogCallerIdentity.require(header);
        log.info("业务服务[{}]确认日志上传 objectId[{}]", callerService, objectId);
        CompleteLogObjectUploadCmd cmd = CompleteLogObjectUploadCmd.builder()
                .objectId(objectId)
                .callerService(callerService)
                .build();
        return LogObjectWebAssembler.toResponse(logObjectAppService.complete(cmd));
    }

    /**
     * 申请短期下载地址。
     *
     * @param objectId 对象标识
     * @param header   调用方服务名请求头
     * @return 短期下载地址
     */
    @PostMapping("/objects/{objectId}/download-url")
    public LogObjectDownloadUrlResponse generateDownloadUrl(
            @PathVariable("objectId") String objectId,
            @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false) String header) {
        String callerService = LogCallerIdentity.require(header);
        log.info("业务服务[{}]申请日志下载地址 objectId[{}]", callerService, objectId);
        return LogObjectWebAssembler.toResponse(logObjectAppService.downloadUrl(objectId, callerService));
    }

    /**
     * 调用方隔离的数据库分页查询。
     *
     * @param businessType 业务类型
     * @param businessId   业务主标识
     * @param state        上传状态
     * @param beginTime    授权时间下界（含），epoch milliseconds
     * @param endTime      授权时间上界（不含），epoch milliseconds
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @param header       调用方服务名请求头
     * @return 分页结果
     */
    @GetMapping("/objects")
    public LogObjectPageResponse listObjects(
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "beginTime", required = false) Long beginTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestHeader(name = CustomHeaders.CALLER_SERVICE_NAME, required = false) String header) {
        String callerService = LogCallerIdentity.require(header);
        int effectivePageNum = pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
        int effectivePageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (effectivePageNum <= 0 || effectivePageSize <= 0 || effectivePageSize > MAX_PAGE_SIZE) {
            throw new OssException(OssErrorCode.PARAMETER_INVALID, "invalid pageNum or pageSize");
        }
        log.info("业务服务[{}]分页查询日志对象 pageNum[{}] pageSize[{}]", callerService, effectivePageNum,
                effectivePageSize);
        PageUtil.startPage(effectivePageNum, effectivePageSize);
        LogObjectQuery query = LogObjectQuery.builder()
                .callerService(callerService)
                .businessType(businessType)
                .businessId(businessId)
                .state(state)
                .beginTime(toInstant(beginTime))
                .endTime(toInstant(endTime))
                .build();
        List<LogObjectResult> results = logObjectAppService.search(query);
        PageResult<LogObjectResponse> pageResult = getPageResult(
                PageUtil.convert(results, LogObjectWebAssembler::toResponse));
        int responsePageNum = effectivePageNum;
        int responsePageSize = effectivePageSize;
        if (results instanceof Page<?> page) {
            responsePageNum = page.getPageNum();
            responsePageSize = page.getPageSize();
        }
        return LogObjectPageResponse.builder()
                .total(pageResult.getTotal())
                .pageNum(responsePageNum)
                .pageSize(responsePageSize)
                .items(pageResult.getItems())
                .build();
    }

    private Instant toInstant(Long epochMilli) {
        return epochMilli == null ? null : Instant.ofEpochMilli(epochMilli);
    }

}
