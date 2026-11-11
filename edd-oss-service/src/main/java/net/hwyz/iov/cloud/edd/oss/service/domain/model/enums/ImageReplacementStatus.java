package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

/**
 * 图片替换任务状态（EDD-OSS-DSN-CR-002 §5.5）。
 * <p>
 * 状态流转：PENDING → PROMOTING → COMPLETED | FAILED；PENDING → CANCELLED。
 * 过期只适用于 PENDING；PROMOTING 允许继续恢复，不因过期丢弃故障现场。
 *
 * @author hwyz_leo
 */
public enum ImageReplacementStatus {

    /**
     * 等待临时上传或完成确认
     */
    PENDING,
    /**
     * 已核验临时对象，正在备份、提升或提交
     */
    PROMOTING,
    /**
     * 图片记录和替换任务均已提交
     */
    COMPLETED,
    /**
     * 提升失败且旧对象已恢复确认；需要重新创建替换任务
     */
    FAILED,
    /**
     * 任务被删除流程取消；不得再提升
     */
    CANCELLED

}
