package com.profetai.industrymap.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分頁查詢結果的共用外殼。
 *
 * <p>總筆數是前端顯示「共 N 項」與判斷還有沒有下一頁的必要資訊，因此不只回本頁內容（design D6）。
 * 總頁數在這裡導出而非讓每個呼叫端各自計算，避免同一套除法散落在多處。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分頁查詢結果")
public class PageResponse<T> {

    @Schema(description = "本頁內容；無符合資料時為空陣列")
    private List<T> content;

    @Schema(description = "頁碼，自 0 起算", example = "0")
    private int page;

    @Schema(description = "每頁筆數", example = "20")
    private int size;

    @Schema(description = "符合條件的總筆數", example = "42")
    private long totalElements;

    @Schema(description = "總頁數", example = "3")
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size))
                .build();
    }
}
