package net.hwyz.iov.cloud.edd.oss.service.adapter.web;

import net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.service.ServiceImageController;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.CompleteImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd.GenerateImageUploadCmd;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageUploadResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssExceptionHandler;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.framework.common.constant.CustomHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service Image API 控制器测试（EDD-OSS-DSN-CR-001 §10.1、§15.3）。
 *
 * @author hwyz_leo
 */
class ServiceImageControllerTest {

    private static final String UPLOAD_PATH = "/api/service/image/v1/uploads";
    private static final String COMPLETE_PATH = "/api/service/image/v1/uploads/object-id/complete";

    private ImageAppService imageAppService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        imageAppService = mock(ImageAppService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServiceImageController(imageAppService))
                .setControllerAdvice(new OssExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("服务端路径与 Feign 客户端契约一致")
    void matchesFeignContractPaths() throws Exception {
        assertPath(net.hwyz.iov.cloud.edd.oss.api.service.ImageService.class, "generateUpload", "/uploads");
        assertPath(net.hwyz.iov.cloud.edd.oss.api.service.ImageService.class, "completeUpload",
                "/uploads/{objectId}/complete");
        assertPath(ServiceImageController.class, "generateUpload", "/uploads");
        assertPath(ServiceImageController.class, "completeUpload", "/uploads/{objectId}/complete");
    }

    private void assertPath(Class<?> type, String methodName, String expectedPath) throws Exception {
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            org.junit.jupiter.api.Assertions.assertNotNull(mapping, "missing PostMapping: " + methodName);
            org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{expectedPath}, mapping.value());
            return;
        }
        throw new AssertionError("method not found: " + methodName);
    }

    @Test
    @DisplayName("申请上传返回 objectId/objectKey/uploadUrl/PUT/Content-Type/epoch 过期时间")
    void generateUpload() throws Exception {
        when(imageAppService.generate(any(GenerateImageUploadCmd.class), eq("tsp-account")))
                .thenReturn(ImageUploadResult.builder()
                        .objectId("object-id")
                        .objectKey("images/tsp-account/object-id.jpg")
                        .uploadUrl("https://signed.example.com/object")
                        .httpMethod("PUT")
                        .contentType("image/jpeg")
                        .expiresAt(Instant.ofEpochMilli(1762757400000L))
                        .build());
        mockMvc.perform(post(UPLOAD_PATH)
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"avatar.jpg\","
                                + "\"contentType\":\"image/jpeg\",\"size\":204800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("object-id"))
                .andExpect(jsonPath("$.objectKey").value("images/tsp-account/object-id.jpg"))
                .andExpect(jsonPath("$.httpMethod").value("PUT"))
                .andExpect(jsonPath("$.expiresAt").value(1762757400000L))
                .andExpect(jsonPath("$.secretId").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist());
        ArgumentCaptor<GenerateImageUploadCmd> captor = ArgumentCaptor.forClass(GenerateImageUploadCmd.class);
        verify(imageAppService).generate(captor.capture(), eq("tsp-account"));
        assertEquals("avatar.jpg", captor.getValue().getOriginalFilename());
        assertEquals(204800L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("请求参数非法返回 400 OSS-400001，且不进入应用服务")
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post(UPLOAD_PATH)
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"\","
                                + "\"contentType\":\"image/jpeg\",\"size\":204800}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
    }

    @Test
    @DisplayName("缺少 X-Caller-Service 返回 401")
    void rejectsMissingCallerContext() throws Exception {
        mockMvc.perform(post(UPLOAD_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"avatar.jpg\","
                                + "\"contentType\":\"image/jpeg\",\"size\":204800}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(OssErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    @DisplayName("X-Caller-Service 格式非法返回 400")
    void rejectsInvalidCallerServiceName() throws Exception {
        mockMvc.perform(post(UPLOAD_PATH)
                        .header(CustomHeaders.CALLER_SERVICE_NAME, "TSP_ACCOUNT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"avatar.jpg\","
                                + "\"contentType\":\"image/jpeg\",\"size\":204800}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OssErrorCode.PARAMETER_INVALID.getCode()));
    }

    @Test
    @DisplayName("完成确认使用认证上下文，返回完整对象信息且时间为 epoch millis")
    void completeUpload() throws Exception {
        ImageObjectResult result = ImageObjectResult.builder()
                .objectId("object-id")
                .serviceName("tsp-account")
                .assetName("默认用户头像")
                .description("未设置头像时使用")
                .originalFilename("avatar.jpg")
                .objectKey("images/tsp-account/object-id.jpg")
                .provider("TENCENT_COS")
                .region("ap-shanghai")
                .bucketName("iov-public-1253442587")
                .permission("PUBLIC_READ_PRIVATE_WRITE")
                .declaredContentType(ImageMimeType.IMAGE_JPEG)
                .declaredSize(204800L)
                .actualContentType(ImageMimeType.IMAGE_JPEG)
                .actualSize(204800L)
                .etag("etag")
                .accessUrl("https://example.com/object.jpg")
                .status(ImageStatus.UPLOADED)
                .expireTime(Instant.ofEpochMilli(1762757400000L))
                .uploadTime(Instant.ofEpochMilli(1762757100000L))
                .createBy("tsp-account")
                .createTime(Instant.ofEpochMilli(1762757000000L))
                .build();
        when(imageAppService.complete(any(CompleteImageUploadCmd.class))).thenReturn(result);
        mockMvc.perform(post(COMPLETE_PATH).header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("object-id"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.declaredContentType").value("image/jpeg"))
                .andExpect(jsonPath("$.expireTime").value(1762757400000L))
                .andExpect(jsonPath("$.uploadTime").value(1762757100000L))
                .andExpect(jsonPath("$.accessUrl").value("https://example.com/object.jpg"))
                .andExpect(jsonPath("$.createBy").doesNotExist())
                .andExpect(jsonPath("$.createTime").doesNotExist())
                .andExpect(jsonPath("$.assetName").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
        ArgumentCaptor<CompleteImageUploadCmd> captor = ArgumentCaptor.forClass(CompleteImageUploadCmd.class);
        verify(imageAppService).complete(captor.capture());
        assertEquals("tsp-account", captor.getValue().getCallerServiceName());
        assertEquals("object-id", captor.getValue().getObjectId());
    }

    @Test
    @DisplayName("其他调用方任务返回统一 404 错误体")
    void hidesOtherCallerObject() throws Exception {
        when(imageAppService.complete(any(CompleteImageUploadCmd.class)))
                .thenThrow(new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        mockMvc.perform(post(COMPLETE_PATH).header(CustomHeaders.CALLER_SERVICE_NAME, "tsp-account"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(OssErrorCode.OBJECT_NOT_FOUND.getCode()));
    }

}
