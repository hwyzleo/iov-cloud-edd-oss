package net.hwyz.iov.cloud.edd.oss.service.infrastructure;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectStorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COS 对象存储网关单元测试（EDD-OSS-DSN-CR-001 §8、§15.5）。
 * <p>
 * 真实 COS 联调见 {@code CosObjectStorageGatewayIT}（需测试 Bucket 凭据）。
 *
 * @author hwyz_leo
 */
class CosObjectStorageGatewayTest {

    private static final String BUCKET = "iov-public-1253442587";
    private static final String KEY = "images/tsp-account/2d27a68e-2ee6-4a0e-a15f-fca2a451a7a3.jpg";

    private COSClient cosClient;
    private CosObjectStorageGateway gateway;

    @BeforeEach
    void setUp() {
        cosClient = mock(COSClient.class);
        gateway = new CosObjectStorageGateway(cosClient);
    }

    @Test
    @DisplayName("预签名必须携带 Content-Type、PUT 方法与过期时间")
    void generateUploadUrlSignsContentTypeAndExpiration() throws Exception {
        when(cosClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://signed.example.com/object"));
        Instant expireTime = Instant.parse("2026-11-11T10:15:00Z");
        String url = gateway.generateUploadUrl(BUCKET, KEY, "image/jpeg", expireTime);
        assertEquals("https://signed.example.com/object", url);
        ArgumentCaptor<GeneratePresignedUrlRequest> captor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(cosClient).generatePresignedUrl(captor.capture());
        GeneratePresignedUrlRequest request = captor.getValue();
        assertEquals(BUCKET, request.getBucketName());
        assertEquals(KEY, request.getKey());
        assertEquals(HttpMethodName.PUT, request.getMethod());
        assertEquals("image/jpeg", request.getContentType());
        Date expiration = request.getExpiration();
        assertEquals(expireTime.toEpochMilli(), expiration.getTime());
    }

    @Test
    @DisplayName("签名失败映射为存储网关异常")
    void generateUploadUrlFailure() {
        when(cosClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenThrow(new CosClientException("network"));
        OssException exception = assertThrows(OssException.class,
                () -> gateway.generateUploadUrl(BUCKET, KEY, "image/jpeg", Instant.now()));
        assertEquals(OssErrorCode.STORAGE_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("HEAD 成功映射 size/contentType/etag")
    void headObject() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/jpeg");
        metadata.setContentLength(204800L);
        metadata.setHeader("ETag", "\"etag-value\"");
        when(cosClient.getObjectMetadata(BUCKET, KEY)).thenReturn(metadata);
        CosObjectMetadata result = gateway.headObject(BUCKET, KEY);
        assertEquals("image/jpeg", result.getContentType());
        assertEquals(204800L, result.getContentLength());
        assertEquals("\"etag-value\"", result.getEtag());
    }

    @Test
    @DisplayName("HEAD 404 返回 null（对象不存在）")
    void headObjectNotFound() {
        CosServiceException notFound = new CosServiceException("not found");
        notFound.setStatusCode(404);
        when(cosClient.getObjectMetadata(BUCKET, KEY)).thenThrow(notFound);
        assertNull(gateway.headObject(BUCKET, KEY));
    }

    @Test
    @DisplayName("HEAD 服务端异常不得误判为对象不存在")
    void headObjectServerFailureIsNotMissing() {
        CosServiceException serverError = new CosServiceException("server error");
        serverError.setStatusCode(500);
        doThrow(serverError).when(cosClient).getObjectMetadata(BUCKET, KEY);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertStorageFailure(() -> gateway.headObject(BUCKET, KEY)));
    }

    @Test
    @DisplayName("HEAD 网络异常不得误判为对象不存在")
    void headObjectNetworkFailureIsNotMissing() {
        doThrow(new CosClientException("timeout")).when(cosClient).getObjectMetadata(BUCKET, KEY);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertStorageFailure(() -> gateway.headObject(BUCKET, KEY)));
    }

    @Test
    @DisplayName("删除：对象不存在按幂等处理")
    void deleteObjectIsIdempotent() {
        CosServiceException notFound = new CosServiceException("not found");
        notFound.setStatusCode(404);
        doThrow(notFound).when(cosClient).deleteObject(BUCKET, KEY);
        gateway.deleteObject(BUCKET, KEY);
        verify(cosClient).deleteObject(BUCKET, KEY);
    }

    @Test
    @DisplayName("删除失败映射为存储网关异常")
    void deleteObjectFailure() {
        CosServiceException serverError = new CosServiceException("server error");
        serverError.setStatusCode(500);
        doThrow(serverError).when(cosClient).deleteObject(BUCKET, KEY);
        assertEquals(OssErrorCode.STORAGE_ERROR, assertStorageFailure(() -> gateway.deleteObject(BUCKET, KEY)));
    }

    @Test
    @DisplayName("公开访问地址按路径段编码且保留 / 分隔符")
    void buildPublicUrl() {
        String url = gateway.buildPublicUrl("ap-shanghai", BUCKET, "images/a b/c+d.jpg");
        assertEquals("https://" + BUCKET + ".cos.ap-shanghai.myqcloud.com/images/a%20b/c%2Bd.jpg", url);
    }

    @Test
    @DisplayName("公开访问地址格式")
    void buildPublicUrlStandardKey() {
        assertEquals("https://" + BUCKET + ".cos.ap-shanghai.myqcloud.com/" + KEY,
                gateway.buildPublicUrl("ap-shanghai", BUCKET, KEY));
    }

    @Test
    @DisplayName("复制：显式传递源与目标 Bucket，并设置 ETag 一致性条件")
    void copyObjectWithEtagConstraint() {
        gateway.copyObject(BUCKET, "source.jpg", BUCKET, "destination.jpg", "\"etag-1\"");
        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(cosClient).copyObject(captor.capture());
        CopyObjectRequest request = captor.getValue();
        assertEquals(BUCKET, request.getSourceBucketName());
        assertEquals("source.jpg", request.getSourceKey());
        assertEquals(BUCKET, request.getDestinationBucketName());
        assertEquals("destination.jpg", request.getDestinationKey());
        assertEquals(List.of("\"etag-1\""), request.getMatchingETagConstraints());
    }

    @Test
    @DisplayName("复制：ETag 为空时不设置条件")
    void copyObjectWithoutEtagConstraint() {
        gateway.copyObject(BUCKET, "source.jpg", BUCKET, "destination.jpg", null);
        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(cosClient).copyObject(captor.capture());
        assertTrue(captor.getValue().getMatchingETagConstraints() == null
                || captor.getValue().getMatchingETagConstraints().isEmpty());
    }

    @Test
    @DisplayName("复制条件冲突映射为 OSS-409004，不误判为对象不存在")
    void copyObjectConflict() {
        CosServiceException conflict = new CosServiceException("precondition failed");
        conflict.setStatusCode(412);
        doThrow(conflict).when(cosClient).copyObject(any(CopyObjectRequest.class));
        OssException exception = assertThrows(OssException.class,
                () -> gateway.copyObject(BUCKET, "source.jpg", BUCKET, "destination.jpg", "etag"));
        assertEquals(OssErrorCode.REPLACEMENT_CONFLICT, exception.getErrorCode());
    }

    @Test
    @DisplayName("复制服务端与网络异常映射为存储网关异常")
    void copyObjectFailure() {
        CosServiceException serverError = new CosServiceException("server error");
        serverError.setStatusCode(500);
        doThrow(serverError).when(cosClient).copyObject(any(CopyObjectRequest.class));
        assertEquals(OssErrorCode.STORAGE_ERROR, assertStorageFailure(
                () -> gateway.copyObject(BUCKET, "source.jpg", BUCKET, "destination.jpg", null)));

        doThrow(new CosClientException("timeout")).when(cosClient).copyObject(any(CopyObjectRequest.class));
        assertEquals(OssErrorCode.STORAGE_ERROR, assertStorageFailure(
                () -> gateway.copyObject(BUCKET, "source.jpg", BUCKET, "destination.jpg", null)));
    }

    private OssErrorCode assertStorageFailure(org.junit.jupiter.api.function.Executable executable) {
        OssException exception = assertThrows(OssException.class, executable);
        return exception.getErrorCode();
    }

}
