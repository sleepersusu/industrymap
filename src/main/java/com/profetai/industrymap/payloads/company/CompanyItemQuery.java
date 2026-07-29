package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公司供應零件的查詢條件。與 {@code SupplierQuery} 對稱——兩者是同一張表的兩個方向。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司供應零件的查詢條件")
public class CompanyItemQuery {

    @Schema(description = "只列該公司以此角色供應的零件；不填表示全取")
    private CompanyRole role;

    @Builder.Default
    @Schema(description = "是否納入草稿的供應角色；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
