package com.profetai.industrymap.payloads.item;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修正品類節點的請求，採全量替換語意（design D2）。
 *
 * <p>不用 PATCH 的決定性理由是 {@code endProduct} 是布林值：JSON 反序列化後無法區分
 * 「沒送這個欄位」與「送了 false」，而「把終端成品改成非終端成品」正是本端點最主要的用例。
 * 三個欄位因此都必須帶齊，缺欄位回 400。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "修正品類節點的請求；採全量替換語意，三個欄位都必須帶齊")
public class AmendItemRequest {

    @NotBlank(message = "名稱為必填")
    @Schema(description = "顯示用名稱，系統會另存正規化名稱作為唯一鍵", example = "主機板")
    private String displayName;

    @NotNull(message = "是否為終端成品為必填")
    @Schema(description = "是否為終端成品", example = "false")
    private Boolean endProduct;

    @Schema(description = "is-a 上層品類 id；欄位必須存在，值可為 null 表示沒有上層品類")
    private Long parentCategoryId;

    /**
     * {@code parentCategoryId} 這個欄位有沒有被送進來。
     *
     * <p>值本身是 null 時，「沒送」與「送了 null」在反序列化後長得一模一樣，
     * 但兩者語意天差地遠：後者是明確要清空上層品類，前者是呼叫端漏帶欄位——
     * 全量替換下若把漏帶當成清空，會靜默弄丟資料。唯一的區分點是
     * setter 有沒有被呼叫過，因此在 setter 上留痕，再由驗證擋成 400。</p>
     */
    @JsonIgnore
    private boolean parentCategoryIdSpecified;

    public void setParentCategoryId(Long parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
        this.parentCategoryIdSpecified = true;
    }

    @JsonIgnore
    @AssertTrue(message = "缺少必填欄位 parentCategoryId（值可為 null，但欄位必須存在）")
    public boolean isParentCategoryIdSpecified() {
        return parentCategoryIdSpecified;
    }
}
