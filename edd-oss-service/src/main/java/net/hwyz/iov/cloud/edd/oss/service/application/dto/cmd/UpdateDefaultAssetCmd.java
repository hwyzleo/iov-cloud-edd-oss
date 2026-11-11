package net.hwyz.iov.cloud.edd.oss.service.application.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编辑默认素材命令（EDD-OSS-DSN-CR-002 §9.3）。
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDefaultAssetCmd {

    private String objectId;
    private String assetName;
    private String description;

}
