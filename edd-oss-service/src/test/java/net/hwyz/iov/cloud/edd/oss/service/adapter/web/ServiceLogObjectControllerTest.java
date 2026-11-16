package net.hwyz.iov.cloud.edd.oss.service.adapter.web;

import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.service.ServiceLogObjectController;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateLogObjectUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectDownloadUrlResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.LogObjectUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.LogObjectAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssExceptionHandler;
import net.hwyz.iov.cloud.framework.common.constant.CustomHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service LogObject API 控制器测试（EDD-OSS-DSN-CR-003 §8）。
 *
 * @author hwyz_leo
 */
class ServiceLogObjectControllerTest {

    private static final String BASE_PATH = "/api/service/logobject/v1";
    private static final String SHA256 = "a".repeat(64);
    private static final String UPLOAD_BODY = "{\"businessType\":\"OTA_UPGRADE_LOG\","
            + "\"businessId\":\"task-1\",\"businessSubId\":\"execution-1\",\"requestId\":\"request-1\","
            + "\"fileName\":\"upgrade.log.gz\",\"contentType\":\"application/gzip\",\"sizeBytes\":1024,"
            + "\"sha256Hex\":\"" + SHA256 + "\",\"privacyLevel\":\"INTERNAL\",\"ttlSeconds\":900}";

    private LogObjectAppService logObjectAppService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logObjectAppService = mock(LogObjectAppService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServiceLogObjectController(logObjectAppService))
                .setControllerAdvice(new OssExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("服务端路径与 Feign 客户端契约一致")
    void matchesFeignContractPaths() throws Exception {
        assertPostPath(net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService.class, "generateUpload", "/uploads");
        assertGetPath(net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService.class, "getObject",
                "/objects/{objectId}");
        assertPostPath(net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService.class, "completeUpload",
                "/objects/{objectId}/complete");
        assertPostPath(net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService.class, "generateDownloadUrl",
                "/objects/{objectId}/download-url");
        assertGetPath(net.hwyz.iov.cloud.edd.oss.api.service.LogObjectService.class, "listObjects", "/objects");
        assertPostPath(ServiceLogObjectController.class, "generateUpload", "/uploads");
        assertGetPath(ServiceLogObjectController.class, "getObject", "/objects/{objectId}");
        assertPostPath(ServiceLogObjectController.class, "completeUpload", "/objects/{objectId}/complete");
        assertPostPath(ServiceLogObjectController.class, "generateDownloadUrl", "/objects/{objectId}/download-url");
        assertGetPath(ServiceLogObjectController.class, "listObjects", "/objects");
    }

    @Test
    @DisplayName("缺失 X-Caller-Service 返回 401 OSS-811006")
    void missingCallerIdentity() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/uploads").contentType(MediaType.APPLICATION_JSON)
                        .content(UPLOAD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(OssErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    @DisplayName("非法调用方格式返回 400 OSS-811001")
    void invalidCallerIdentity() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/uploads")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "TSP-OTA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPLOAD_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
    }

    @Test
    @DisplayName("申请授权返回 objectId/objectKey/PUT URL/强制头/expiresAt/maxSizeBytes")
    void generateUpload() throws Exception {
        when(logObjectAppService.generate(any(GenerateLogObjectUploadCmd.class), eq("tsp-ota")))
                .thenReturn(LogObjectUploadResult.builder()
                        .objectId("object-id")
                        .objectKey("logs/tsp-ota/OTA_UPGRADE_LOG/20261115/object-id")
                        .uploadUrl("https://signed.example.com/put")
                        .headers(Map.of("Content-Type", "application/gzip", "x-cos-meta-sha256", SHA256))
                        .expiresAt(Instant.ofEpochMilli(1763250000000L))
                        .maxSizeBytes(536870912L)
                        .build());
        mockMvc.perform(post(BASE_PATH + "/uploads")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPLOAD_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("object-id"))
                .andExpect(jsonPath("$.objectKey").value("logs/tsp-ota/OTA_UPGRADE_LOG/20261115/object-id"))
                .andExpect(jsonPath("$.headers['Content-Type']").value("application/gzip"))
                .andExpect(jsonPath("$.headers['x-cos-meta-sha256']").value(SHA256))
                .andExpect(jsonPath("$.expiresAt").value(1763250000000L))
                .andExpect(jsonPath("$.maxSizeBytes").value(536870912L))
                .andExpect(jsonPath("$.secretId").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist());
        ArgumentCaptor<GenerateLogObjectUploadCmd> captor =
                ArgumentCaptor.forClass(GenerateLogObjectUploadCmd.class);
        verify(logObjectAppService).generate(captor.capture(), eq("tsp-ota"));
        assertEquals("OTA_UPGRADE_LOG", captor.getValue().getBusinessType());
        assertEquals("task-1", captor.getValue().getBusinessId());
        assertEquals(SHA256, captor.getValue().getSha256Hex());
    }

    @Test
    @DisplayName("请求参数非法返回 400 OSS-811001，且不进入应用服务")
    void rejectsInvalidUploadRequest() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/uploads")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPLOAD_BODY.replace(SHA256, "invalid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
    }

    @Test
    @DisplayName("complete 与下载地址路径委托应用服务")
    void completeAndDownload() throws Exception {
        LogObjectResult result = LogObjectResult.builder()
                .objectId("object-id")
                .uploadState("UPLOADED")
                .authorizedAt(Instant.ofEpochMilli(1763250000000L))
                .uploadedAt(Instant.ofEpochMilli(1763250001000L))
                .build();
        when(logObjectAppService.complete(any(CompleteLogObjectUploadCmd.class))).thenReturn(result);
        mockMvc.perform(post(BASE_PATH + "/objects/object-id/complete")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadState").value("UPLOADED"))
                .andExpect(jsonPath("$.uploadedAt").value(1763250001000L));

        when(logObjectAppService.downloadUrl("object-id", "tsp-ota"))
                .thenReturn(LogObjectDownloadUrlResult.builder()
                        .objectId("object-id")
                        .downloadUrl("https://signed.example.com/get")
                        .expiresAt(Instant.ofEpochMilli(1763250300000L))
                        .build());
        mockMvc.perform(post(BASE_PATH + "/objects/object-id/download-url")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://signed.example.com/get"))
                .andExpect(jsonPath("$.expiresAt").value(1763250300000L));

        when(logObjectAppService.detail("object-id", "tsp-ota")).thenReturn(result);
        mockMvc.perform(get(BASE_PATH + "/objects/object-id")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("object-id"));
    }

    @Test
    @DisplayName("分页默认 pageNum=1/pageSize=10，且响应保留分页元数据")
    void listDefaultsAndMetadata() throws Exception {
        when(logObjectAppService.search(any())).thenReturn(List.of(LogObjectResult.builder()
                .objectId("object-id").uploadState("AUTHORIZED").build()));
        mockMvc.perform(get(BASE_PATH + "/objects")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.items[0].objectId").value("object-id"));
    }

    @Test
    @DisplayName("pageSize 超过 100 或页码非正返回 400 OSS-811001")
    void listRejectsInvalidPaging() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/objects")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota")
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
        mockMvc.perform(get(BASE_PATH + "/objects")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota")
                        .param("pageNum", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
    }

    @Test
    @DisplayName("业务异常使用 code + HTTP 成对响应")
    void mapsBusinessException() throws Exception {
        when(logObjectAppService.complete(any(CompleteLogObjectUploadCmd.class)))
                .thenThrow(new net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException(
                        OssErrorCode.LOG_OBJECT_MISSING));
        mockMvc.perform(post(BASE_PATH + "/objects/object-id/complete")
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-ota"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(OssErrorCode.LOG_OBJECT_MISSING.getCode()));
    }

    private void assertPostPath(Class<?> type, String methodName, String expectedPath) {
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                PostMapping mapping = method.getAnnotation(PostMapping.class);
                assertNotNull(mapping, "missing PostMapping: " + methodName);
                assertEquals(expectedPath, mapping.value()[0]);
                return;
            }
        }
        throw new AssertionError("method not found: " + methodName);
    }

    private void assertGetPath(Class<?> type, String methodName, String expectedPath) {
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                assertNotNull(mapping, "missing GetMapping: " + methodName);
                assertEquals(expectedPath, mapping.value()[0]);
                return;
            }
        }
        throw new AssertionError("method not found: " + methodName);
    }

}
