package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建替换任务命令（EDD-OSS-DSN-CR-002 §9.4）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageReplacementCmd {

    private String objectId;
    private String originalFilename;
    private String contentType;
    private long size;

}
