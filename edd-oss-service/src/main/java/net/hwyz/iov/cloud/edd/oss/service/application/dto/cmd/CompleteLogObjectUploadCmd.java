package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成日志对象上传命令（EDD-OSS-DSN-CR-003 §8.4）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteLogObjectUploadCmd {

    /**
     * 对象标识
     */
    private String objectId;
    /**
     * 调用方服务名
     */
    private String callerService;

}
