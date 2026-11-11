package net.hwyz.iov.cloud.edd.oss.service.domain.model.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.hwyz.iov.cloud.edd.oss.service.domain.model.enums.ImageStatus;

import java.time.Instant;

/**
 * 图片对象搜索条件（EDD-OSS-DSN-CR-001 §10.2.1、§12）。
 * <p>
 * 分页由调用侧平台分页插件驱动；本对象不携带排序字段，排序固定 id DESC。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageObjectSearchCriteria {

    /**
     * 调用方服务名，精确匹配
     */
    private String serviceName;
    /**
     * 指定单个状态；为空表示 PENDING + UPLOADED
     */
    private ImageStatus status;
    /**
     * 图片 MIME：优先匹配实际 MIME，未完成时匹配声明 MIME
     */
    private String contentType;
    /**
     * 对象键，精确匹配
     */
    private String objectKey;
    /**
     * 原始文件名前缀
     */
    private String originalFilenamePrefix;
    /**
     * 上传完成时间下界
     */
    private Instant uploadBeginTime;
    /**
     * 上传完成时间上界
     */
    private Instant uploadEndTime;

}
