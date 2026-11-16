package net.hwyz.iov.cloud.edd.oss.api.service;

import net.hwyz.iov.cloud.edd.oss.api.fallback.LogObjectServiceFallbackFactory;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateLogObjectUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectDownloadUrlResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectPageResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectUploadResponse;
import net.hwyz.iov.cloud.framework.common.constant.ServiceNameConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 通用业务日志对象服务接口（EDD-OSS-DSN-CR-003 §8）。
 * <p>
 * 仅允许内网业务服务通过 X-Caller-Service 调用；业主服务不得持有 COS Secret
 * 或直连私有日志 Bucket。
 *
 * @author hwyz_leo
 */
@FeignClient(contextId = "logObjectService", value = ServiceNameConstants.EDD_OSS,
        path = "/api/service/logobject/v1", fallbackFactory = LogObjectServiceFallbackFactory.class)
public interface LogObjectService {

    /**
     * 申请日志对象上传授权。
     *
     * @param request 上传申请
     * @return 上传参数
     */
    @PostMapping("/uploads")
    LogObjectUploadResponse generateUpload(@RequestBody GenerateLogObjectUploadRequest request);

    /**
     * 查询日志对象登记事实。
     *
     * @param objectId 对象标识
     * @return 登记事实
     */
    @GetMapping("/objects/{objectId}")
    LogObjectResponse getObject(@PathVariable("objectId") String objectId);

    /**
     * 完成日志对象上传核验。
     *
     * @param objectId 对象标识
     * @return 已核验的登记事实
     */
    @PostMapping("/objects/{objectId}/complete")
    LogObjectResponse completeUpload(@PathVariable("objectId") String objectId);

    /**
     * 申请短期下载地址。
     *
     * @param objectId 对象标识
     * @return 短期下载地址
     */
    @PostMapping("/objects/{objectId}/download-url")
    LogObjectDownloadUrlResponse generateDownloadUrl(@PathVariable("objectId") String objectId);

    /**
     * 调用方隔离的数据库分页查询。
     *
     * @param businessType 业务类型
     * @param businessId   业务主标识
     * @param state        上传状态
     * @param beginTime    授权时间下界（含），epoch milliseconds
     * @param endTime      授权时间上界（不含），epoch milliseconds
     * @param pageNum      页码，默认 1
     * @param pageSize     每页条数，默认 10，最大 100
     * @return 分页结果
     */
    @GetMapping("/objects")
    LogObjectPageResponse listObjects(@RequestParam(value = "businessType", required = false) String businessType,
                                      @RequestParam(value = "businessId", required = false) String businessId,
                                      @RequestParam(value = "state", required = false) String state,
                                      @RequestParam(value = "beginTime", required = false) Long beginTime,
                                      @RequestParam(value = "endTime", required = false) Long endTime,
                                      @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                      @RequestParam(value = "pageSize", required = false) Integer pageSize);

}
