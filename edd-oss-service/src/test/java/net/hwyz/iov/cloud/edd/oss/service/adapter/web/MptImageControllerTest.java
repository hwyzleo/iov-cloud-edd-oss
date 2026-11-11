package net.hwyz.iov.cloud.edd.oss.service.adapter.web;

import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.edd.oss.service.adapter.web.controller.mpt.MptImageController;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.query.ImageObjectQuery;
import net.hwyz.iov.cloud.edd.oss.service.application.dto.result.ImageObjectResult;
import net.hwyz.iov.cloud.edd.oss.service.application.service.ImageAppService;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssErrorCode;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssException;
import net.hwyz.iov.cloud.edd.oss.service.common.exception.OssExceptionHandler;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageMimeType;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;
import net.hwyz.iov.cloud.framework.audit.annotation.Log;
import net.hwyz.iov.cloud.framework.audit.enums.BusinessType;
import net.hwyz.iov.cloud.framework.security.annotation.RequiresPermissions;
import net.hwyz.iov.cloud.framework.web.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MPT 图片管理接口测试（EDD-OSS-DSN-CR-001 §10.2、§15.3）。
 *
 * @author hwyz_leo
 */
class MptImageControllerTest {

    private static final String BASE_PATH = "/api/mpt/image/v1/objects";

    private ImageAppService imageAppService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        imageAppService = mock(ImageAppService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MptImageController(imageAppService))
                .setControllerAdvice(new OssExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.remove();
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("列表返回 ApiResponse<PageResult<ImageObjectResponse>> 且传给应用层 status 为原值")
    void list() throws Exception {
        when(imageAppService.search(any(ImageObjectQuery.class)))
                .thenReturn(List.of(result("object-1", ImageStatus.UPLOADED), result("object-2", ImageStatus.PENDING)));
        mockMvc.perform(get(BASE_PATH + "/list").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2));
        ArgumentCaptor<ImageObjectQuery> captor = ArgumentCaptor.forClass(ImageObjectQuery.class);
        verify(imageAppService).search(captor.capture());
        assertEquals(null, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("详情：不存在返回 404 OSS-404001")
    void detailNotFound() throws Exception {
        when(imageAppService.detail("missing")).thenThrow(new OssException(OssErrorCode.OBJECT_NOT_FOUND));
        mockMvc.perform(get(BASE_PATH + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(OssErrorCode.OBJECT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("预览：UPLOADED 可预览并返回地址")
    void previewUploaded() throws Exception {
        when(imageAppService.detail("object-1")).thenReturn(result("object-1", ImageStatus.UPLOADED));
        mockMvc.perform(get(BASE_PATH + "/object-1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(true))
                .andExpect(jsonPath("$.data.previewUrl").value("https://cdn.example.com/object-1.jpg"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("预览：PENDING / DELETED 不可预览且不返回地址")
    void previewNotUploaded() throws Exception {
        ImageObjectResult pending = result("object-1", ImageStatus.PENDING);
        pending.setAccessUrl(null);
        when(imageAppService.detail("object-1")).thenReturn(pending);
        mockMvc.perform(get(BASE_PATH + "/object-1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(false))
                .andExpect(jsonPath("$.data.previewUrl").doesNotExist());

        ImageObjectResult deleted = result("object-2", ImageStatus.DELETED);
        deleted.setAccessUrl(null);
        when(imageAppService.detail("object-2")).thenReturn(deleted);
        mockMvc.perform(get(BASE_PATH + "/object-2/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewable").value(false))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    @DisplayName("删除：操作人取自 SecurityUtils，不由请求体传入")
    void removeUsesAuthenticatedOperator() throws Exception {
        SecurityContextHolder.setUserId("1001");
        SecurityContextHolder.setUserName("admin");
        mockMvc.perform(delete(BASE_PATH + "/object-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"));
        verify(imageAppService).delete("object-1", "1001");
    }

    @Test
    @DisplayName("删除：PENDING 返回 409 OSS-409002")
    void removePendingRejected() throws Exception {
        SecurityContextHolder.setUserId("1001");
        org.mockito.Mockito.doThrow(new OssException(OssErrorCode.INVALID_STATE))
                .when(imageAppService).delete("object-1", "1001");
        mockMvc.perform(delete(BASE_PATH + "/object-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(OssErrorCode.INVALID_STATE.getCode()));
    }

    @Test
    @DisplayName("权限点与审计注解齐全")
    void permissionAndAuditAnnotations() throws Exception {
        assertPermission("list", "oss:image:list");
        assertPermission("detail", "oss:image:query");
        assertPermission("preview", "oss:image:preview");
        assertPermission("remove", "oss:image:remove");
        Method remove = MptImageController.class.getMethod("remove", String.class);
        Log log = remove.getAnnotation(Log.class);
        assertNotNull(log);
        assertEquals(BusinessType.DELETE, log.businessType());
        assertNotNull(MptImageController.class.getMethod("list",
                net.hwyz.iov.cloud.edd.oss.service.adapter.web.vo.request.ImageObjectQueryRequest.class)
                .getAnnotation(GetMapping.class));
        assertNotNull(MptImageController.class.getMethod("remove", String.class)
                .getAnnotation(DeleteMapping.class));
    }

    @Test
    @DisplayName("旧通用对象接口不再存在")
    void legacyEndpointIsGone() throws Exception {
        mockMvc.perform(get("/service/object/action/generatePreSignedUrl"))
                .andExpect(status().isNotFound());
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        for (Method method : MptImageController.class.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            RequiresPermissions permission = method.getAnnotation(RequiresPermissions.class);
            assertNotNull(permission, "缺失权限注解: " + methodName);
            assertTrue(java.util.Arrays.asList(permission.value()).contains(expected),
                    "权限点不匹配: " + methodName);
            return;
        }
        throw new AssertionError("未找到方法: " + methodName);
    }

    private ImageObjectResult result(String objectId, ImageStatus status) {
        return ImageObjectResult.builder()
                .objectId(objectId)
                .serviceName("tsp-account")
                .originalFilename("avatar.jpg")
                .objectKey("images/tsp-account/" + objectId + ".jpg")
                .provider("TENCENT_COS")
                .region("ap-shanghai")
                .bucketName("iov-public-1253442587")
                .permission("PUBLIC_READ_PRIVATE_WRITE")
                .declaredContentType(ImageMimeType.IMAGE_JPEG)
                .declaredSize(204800L)
                .actualContentType(status == ImageStatus.PENDING ? null : ImageMimeType.IMAGE_JPEG)
                .actualSize(status == ImageStatus.PENDING ? null : 204800L)
                .etag(status == ImageStatus.PENDING ? null : "etag")
                .accessUrl(status == ImageStatus.UPLOADED ? "https://cdn.example.com/" + objectId + ".jpg" : null)
                .status(status)
                .expireTime(Instant.ofEpochMilli(1762757400000L))
                .uploadTime(status == ImageStatus.PENDING ? null : Instant.ofEpochMilli(1762757100000L))
                .build();
    }

}
