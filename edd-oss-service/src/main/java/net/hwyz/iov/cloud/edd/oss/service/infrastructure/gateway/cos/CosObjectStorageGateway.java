package net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.edd.oss.service.application.port.gateway.ObjectStorageGateway;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 腾讯云 COS 对象存储网关实现（EDD-OSS-DSN-CR-001 §8）。
 * <p>
 * 只在本类接触 COS SDK；不把 SDK 异常类型泄露给应用层。
 *
 * @author hwyz_leo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CosObjectStorageGateway implements ObjectStorageGateway {

    private static final int NOT_FOUND_STATUS = 404;

    private final COSClient cosClient;

    @Override
    public String generateUploadUrl(String bucket, String key, String contentType, Instant expireTime) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.PUT);
        request.setExpiration(Date.from(expireTime));
        // 将 Content-Type 纳入签名：上传时必须携带相同 Content-Type
        request.setContentType(contentType);
        try {
            return cosClient.generatePresignedUrl(request).toString();
        } catch (CosClientException exception) {
            log.error("COS 预签名失败 bucket[{}]", bucket, exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to generate upload url", exception);
        }
    }

    @Override
    public CosObjectMetadata headObject(String bucket, String key) {
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(bucket, key);
            return CosObjectMetadata.builder()
                    .contentType(metadata.getContentType())
                    .contentLength(metadata.getContentLength())
                    .etag(metadata.getETag())
                    .build();
        } catch (CosServiceException exception) {
            if (exception.getStatusCode() == NOT_FOUND_STATUS) {
                return null;
            }
            log.error("COS HEAD 失败 bucket[{}] key[{}] status[{}]", bucket, key, exception.getStatusCode(), exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to head object", exception);
        } catch (CosClientException exception) {
            log.error("COS HEAD 客户端异常 bucket[{}] key[{}]", bucket, key, exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to head object", exception);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            cosClient.deleteObject(bucket, key);
        } catch (CosServiceException exception) {
            if (exception.getStatusCode() == NOT_FOUND_STATUS) {
                // 对象不存在按幂等删除处理，支持删除重试
                return;
            }
            log.error("COS 删除失败 bucket[{}] key[{}] status[{}]", bucket, key, exception.getStatusCode(), exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to delete object", exception);
        } catch (CosClientException exception) {
            log.error("COS 删除客户端异常 bucket[{}] key[{}]", bucket, key, exception);
            throw new OssException(OssErrorCode.STORAGE_ERROR, "failed to delete object", exception);
        }
    }

    @Override
    public String buildPublicUrl(String region, String bucket, String key) {
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + encodeObjectKey(key);
    }

    private String encodeObjectKey(String key) {
        return Arrays.stream(key.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

}
