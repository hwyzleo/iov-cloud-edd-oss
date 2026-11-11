package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请默认素材上传命令（EDD-OSS-DSN-CR-002 §9.1）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDefaultAssetCmd {

    private String assetName;
    private String description;
    private String originalFilename;
    private String contentType;
    private long size;

}
