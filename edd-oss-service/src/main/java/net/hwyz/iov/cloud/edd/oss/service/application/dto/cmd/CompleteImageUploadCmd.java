package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成图片上传命令（EDD-OSS-DSN-CR-001 §9.2）。
 * <p>
 * 调用方服务名只来自认证上下文，不接收请求体自报值。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteImageUploadCmd {

    private String objectId;
    private String callerServiceName;
    /**
     * 后台管理操作人；Service API 为空时由仓储回退为调用方服务名（EDD-OSS-DSN-CR-002 §9.2）
     */
    private String operator;

}
