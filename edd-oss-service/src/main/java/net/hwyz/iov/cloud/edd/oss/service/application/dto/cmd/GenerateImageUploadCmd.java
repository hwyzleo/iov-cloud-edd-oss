package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请图片上传命令（EDD-OSS-DSN-CR-001 §9.1）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageUploadCmd {

    private String originalFilename;
    private String contentType;
    private long size;

}
