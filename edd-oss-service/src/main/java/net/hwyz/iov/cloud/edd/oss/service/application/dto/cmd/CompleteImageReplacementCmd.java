package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成或恢复替换命令（EDD-OSS-DSN-CR-002 §9.5）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteImageReplacementCmd {

    private String objectId;
    private String replacementId;
    private String operator;

}
