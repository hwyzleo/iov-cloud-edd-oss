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

}
