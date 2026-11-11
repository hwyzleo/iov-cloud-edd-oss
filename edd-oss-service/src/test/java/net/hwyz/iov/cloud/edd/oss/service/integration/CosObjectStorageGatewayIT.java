package net.hwyz.iov.cloud.edd.oss.service.integration;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectMetadata;
import net.hwyz.iov.cloud.edd.oss.service.infrastructure.gateway.cos.CosObjectStorageGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COS 真实联调集成测试（EDD-OSS-DSN-CR-001 §15.5）。
 * <p>
 * 只能对<b>专用测试 Bucket</b>执行，禁止指向生产 Bucket。执行方式：
 * <pre>
 * IT_COS_REGION=ap-shanghai IT_COS_BUCKET=&lt;test-bucket&gt; \
 * IT_COS_SECRET_ID=... IT_COS_SECRET_KEY=... \
 * mvn test -Dtest=CosObjectStorageGatewayIT
 * </pre>
 * 缺少凭据时显式跳过（不伪造通过）。
 *
 * @author hwyz_leo
 */
@EnabledIfEnvironmentVariable(named = "IT_COS_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "IT_COS_SECRET_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "IT_COS_SECRET_KEY", matches = ".+")
class CosObjectStorageGatewayIT {

    private static final String JPEG_BYTES = "fake-jpeg-content";

    private static COSClient cosClient;
    private static CosObjectStorageGateway gateway;
    private static String bucket;
    private static String region;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() {
        region = System.getenv("IT_COS_REGION");
        bucket = System.getenv("IT_COS_BUCKET");
        String secretId = System.getenv("IT_COS_SECRET_ID");
        String secretKey = System.getenv("IT_COS_SECRET_KEY");
        Assumptions.assumeTrue(region != null && !region.isBlank(), "IT_COS_REGION 未提供");
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        cosClient = new COSClient(new BasicCOSCredentials(secretId, secretKey), clientConfig);
        gateway = new CosObjectStorageGateway(cosClient);
    }

    @AfterAll
    static void tearDown() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    @Test
    @DisplayName("签名 URL 可 PUT 并携带 Content-Type，HEAD 读取元数据，公开 URL 可访问，删除后不可访问")
    void uploadHeadDeleteRoundTrip() throws Exception {
        String key = uniqueKey("jpg");
        String uploadUrl = gateway.generateUploadUrl(bucket, key, "image/jpeg", Instant.now().plusSeconds(300));
        HttpResponse<String> put = put(uploadUrl, "image/jpeg", JPEG_BYTES);
        assertEquals(200, put.statusCode(), "预签名上传失败: " + put.body());

        CosObjectMetadata metadata = gateway.headObject(bucket, key);
        assertNotNull(metadata);
        assertEquals("image/jpeg", metadata.getContentType());
        assertEquals(JPEG_BYTES.getBytes(StandardCharsets.UTF_8).length, metadata.getContentLength());
        assertNotNull(metadata.getEtag());

        String publicUrl = gateway.buildPublicUrl(region, bucket, key);
        assertEquals(200, get(publicUrl).statusCode());

        gateway.deleteObject(bucket, key);
        assertEquals(404, get(publicUrl).statusCode());
    }

    @Test
    @DisplayName("使用错误 Content-Type 时 COS 拒绝上传")
    void wrongContentTypeRejected() throws Exception {
        String key = uniqueKey("jpg");
        String uploadUrl = gateway.generateUploadUrl(bucket, key, "image/jpeg", Instant.now().plusSeconds(300));
        HttpResponse<String> put = put(uploadUrl, "image/png", JPEG_BYTES);
        assertTrue(put.statusCode() == 403 || put.statusCode() == 400, "错误 Content-Type 未被拒绝: " + put.statusCode());
        gateway.deleteObject(bucket, key);
    }

    @Test
    @DisplayName("签名 URL 过期后拒绝上传")
    void expiredUrlRejected() throws Exception {
        String key = uniqueKey("jpg");
        String uploadUrl = gateway.generateUploadUrl(bucket, key, "image/jpeg", Instant.now().minusSeconds(60));
        HttpResponse<String> put = put(uploadUrl, "image/jpeg", JPEG_BYTES);
        assertEquals(403, put.statusCode());
    }

    @Test
    @DisplayName("HEAD 对不存在对象返回 null")
    void headMissingObjectReturnsNull() {
        assertEquals(null, gateway.headObject(bucket, uniqueKey("jpg")));
    }

    @Test
    @DisplayName("删除不存在对象按幂等处理")
    void deleteMissingObjectIsIdempotent() {
        gateway.deleteObject(bucket, uniqueKey("jpg"));
    }

    private HttpResponse<String> put(String url, String contentType, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String uniqueKey(String extension) {
        return "images/it/" + UUID.randomUUID() + "." + extension;
    }

}
