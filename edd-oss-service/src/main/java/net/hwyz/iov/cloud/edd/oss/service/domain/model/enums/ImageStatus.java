package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

/**
 * 图片对象状态（EDD-OSS-DSN-CR-001 §5.1）。
 * <p>
 * 过期是时间属性，不引入 EXPIRED 状态。
 *
 * @author hwyz_leo
 */
public enum ImageStatus {

    /**
     * 上传任务已创建，对象尚未确认
     */
    PENDING,
    /**
     * 上传完成且已核验
     */
    UPLOADED,
    /**
     * 已删除（保留数据库记录）
     */
    DELETED

}
