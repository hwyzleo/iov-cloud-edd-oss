package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

/**
 * 日志对象确定性失败原因（EDD-OSS-DSN-CR-003 §4.1、§8.4）。
 * <p>
 * 保存稳定原因码，不保存日志正文、预签名 URL 或异常堆栈。
 *
 * @author hwyz_leo
 */
public enum LogFailureReason {

    /**
     * COS 物理对象不存在
     */
    OBJECT_NOT_FOUND,
    /**
     * HEAD 实际大小与声明不一致
     */
    SIZE_MISMATCH,
    /**
     * 摘要元数据缺失或与声明不一致
     */
    DIGEST_MISMATCH,
    /**
     * 实际 Content-Type 与声明不一致
     */
    CONTENT_TYPE_MISMATCH

}
