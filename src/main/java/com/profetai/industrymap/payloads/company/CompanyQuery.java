package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公司列表的查詢條件。比照 {@code EndProductQuery} 以 payload 承載而非逐個 query 參數，
 * 分頁邊界的驗證規則因此跟著條件本身走，不散落在 controller 簽章上。
 *
 * <p>過濾條件一律用包裝型別：{@code null} 代表「未指定」＝不過濾（spec 明訂）。
 * 用 {@code boolean} / {@code long} 基本型別的話，「沒帶 publicCompany」與「publicCompany=false」
 * 會塌成同一個值，未上市公司的過濾就永遠關不掉。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司列表查詢條件")
public class CompanyQuery {

    @Min(value = 0, message = "頁碼不可為負數")
    @Builder.Default
    @Schema(description = "頁碼，自 0 起算", example = "0")
    private int page = 0;

    @Min(value = 1, message = "每頁筆數至少為 1")
    @Max(value = 100, message = "每頁筆數上限為 100")
    @Builder.Default
    @Schema(description = "每頁筆數 1–100；上限存在是為了避免一次拉出整張清單", example = "20")
    private int size = 20;

    @Schema(description = "名稱關鍵字，正規化後對公司名稱與其別名做包含比對；不填表示不過濾",
            example = "桂盟")
    private String name;

    @Schema(description = "所屬國家，ISO 3166-1 兩碼，不分大小寫的精確比對；不填表示不過濾",
            example = "TW")
    private String country;

    @Schema(description = "是否為公開發行公司；不填表示不過濾", example = "true")
    private Boolean publicCompany;

    @Schema(description = "品類節點 id，只回傳對該零件具有供應角色的公司；不填表示不過濾", example = "12")
    private Long itemId;

    @Schema(description = "供應角色，僅在指定 itemId 時有意義；不填表示不限角色", example = "MANUFACTURE")
    private CompanyRole companyRole;

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
