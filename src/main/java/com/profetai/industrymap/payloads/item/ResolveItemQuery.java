package com.profetai.industrymap.payloads.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "以名稱或別名解析品類節點的查詢條件")
public class ResolveItemQuery {

    @NotBlank(message = "名稱為必填")
    @Schema(description = "名稱或別名，系統會正規化後比對", example = "無線網卡")
    private String name;
}
