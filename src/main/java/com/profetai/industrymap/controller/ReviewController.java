package com.profetai.industrymap.controller;

import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.review.BatchReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.service.review.BatchReviewService;
import com.profetai.industrymap.service.review.ReviewApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 審核狀態流轉（design D1）。
 *
 * <p>八張內容表的審核行為完全一致，因此收斂成單一端點以「資料類型 + 識別碼」指定目標，
 * 而不是在每個資源底下各開一組——批次審核天生跨類型（建完一台腳踏車後一次審掉節點、
 * 關係、公司與市佔率），分散的端點做不到。</p>
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "內容資料的審核狀態流轉")
public class ReviewController {

    private final ReviewApplyService reviewApplyService;
    private final BatchReviewService batchReviewService;

    @PostMapping
    @Operation(summary = "審核單筆資料",
            description = "支援的目標類型：ITEM、ITEM_ALIAS、ITEM_COMPOSITION、COMPANY、COMPANY_ALIAS、"
                    + "COMPANY_IDENTIFIER、COMPANY_ITEM_ROLE、MARKET_SHARE。"
                    + "目標可用內部 id（targetId）或自然鍵（naturalKey）定位，擇一提供即可；"
                    + "兩者同時提供時以 targetId 為準。各類型的自然鍵組合見 ReviewTargetKey。"
                    + "轉為 VERIFIED / REJECTED 需提供審核者；退回 DRAFT 會清空審核者與審核時間。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "審核完成，回傳更新後的狀態"),
            @ApiResponse(responseCode = "400", description = "不支援的目標類型、兩種定位方式皆未提供、"
                    + "自然鍵欄位不足（訊息會指出缺哪些維度）或未提供審核者"),
            @ApiResponse(responseCode = "404", description = "該類型底下查無此識別碼或自然鍵")
    })
    public ResponseEntity<ServerResponse<ReviewResultResponse>> review(@Valid @RequestBody ReviewRequest request) {
        return ServerResponses.ok(reviewApplyService.apply(
                request.getTargetType(), request.getTargetId(), request.getNaturalKey(),
                request.getTargetStatus(), request.getReviewer()));
    }

    @PostMapping("/batch")
    @Operation(summary = "批次審核",
            description = "各筆可屬於不同資料類型，也可分別以內部 id 或自然鍵定位；"
                    + "個別失敗不影響其他項目，逐筆回報成功或失敗原因。"
                    + "批次建立端點的回應可直接轉為本請求的 targets，不需再查詢任何端點。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次、不支援的目標類型、"
                    + "某筆兩種定位方式皆未提供，或未提供審核者")
    })
    public ResponseEntity<ServerResponse<List<ReviewResultResponse>>> reviewBatch(
            @Valid @RequestBody BatchReviewRequest request) {

        return ServerResponses.ok(batchReviewService.applyBatch(request));
    }
}
