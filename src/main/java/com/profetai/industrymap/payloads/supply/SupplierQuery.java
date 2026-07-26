package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "零件供應公司查詢條件")
public class SupplierQuery {

    @Schema(description = "只取指定角色，例如只看代工組裝商；不填表示全取")
    private CompanyRole role;

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
