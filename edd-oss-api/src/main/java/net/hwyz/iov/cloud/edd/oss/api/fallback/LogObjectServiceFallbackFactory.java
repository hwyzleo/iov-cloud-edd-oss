package net.hwyz.iov.cloud.edd.oss.api.fallback;

import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService;
import net.hwyz.iov.cloud.edd.oss.api.vo.request.GenerateLogObjectUploadRequest;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectDownloadUrlResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectPageResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectResponse;
import net.hwyz.iov.cloud.edd.oss.api.vo.response.LogObjectUploadResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 日志对象服务降级工厂（EDD-OSS-DSN-CR-003 §8.1）。
 * <p>
 * 必须 fail-closed：不得返回伪造的 objectId、上传/下载地址、UPLOADED 或成功结果。
 *
 * @author hwyz_leo
 */
@Slf4j
@Component
public class LogObjectServiceFallbackFactory implements FallbackFactory<LogObjectService> {

    @Override
    public LogObjectService create(Throwable cause) {
        log.error("[OSS] 日志对象服务调用失败，进入降级", cause);
        return new LogObjectService() {
            @Override
            public LogObjectUploadResponse generateUpload(GenerateLogObjectUploadRequest request) {
                throw new IllegalStateException("日志对象服务不可用，无法申请上传授权", cause);
            }

            @Override
            public LogObjectResponse getObject(String objectId) {
                throw new IllegalStateException("日志对象服务不可用，无法查询对象[" + objectId + "]", cause);
            }

            @Override
            public LogObjectResponse completeUpload(String objectId) {
                throw new IllegalStateException("日志对象服务不可用，无法完成上传核验[" + objectId + "]", cause);
            }

            @Override
            public LogObjectDownloadUrlResponse generateDownloadUrl(String objectId) {
                throw new IllegalStateException("日志对象服务不可用，无法签发下载地址[" + objectId + "]", cause);
            }

            @Override
            public LogObjectPageResponse listObjects(String businessType, String businessId, String state,
                                                     Long beginTime, Long endTime, Integer pageNum,
                                                     Integer pageSize) {
                throw new IllegalStateException("日志对象服务不可用，无法分页查询", cause);
            }
        };
    }

}
