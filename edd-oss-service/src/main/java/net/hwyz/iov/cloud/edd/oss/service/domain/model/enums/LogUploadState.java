package net.hwyz.iov.cloud.edd.oss.service.domain.model.enums;

/**
 * 日志对象上传状态（EDD-OSS-DSN-CR-003 §4.2）。
 * <p>
 * 本期不启用 UPLOADING；{@code FAILED} 与 {@code EXPIRED} 为终态。
 *
 * @author hwyz_leo
 */
public enum LogUploadState {

    /**
     * 已授权，等待真实 PUT 与 complete 核验
     */
    AUTHORIZED,
    /**
     * 已通过 HEAD 核验并完成条件更新
     */
    UPLOADED,
    /**
     * 确定性核验失败，终态
     */
    FAILED,
    /**
     * 上传授权已过期，终态
     */
    EXPIRED

}
