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
                    + "轉為 VERIFIED / REJECTED 需提供審核者；退回 DRAFT 會清空審核者與審核時間。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "審核完成，回傳更新後的狀態"),
            @ApiResponse(responseCode = "400", description = "不支援的目標類型、缺少必填欄位或未提供審核者"),
            @ApiResponse(responseCode = "404", description = "該類型底下查無此識別碼")
    })
    public ResponseEntity<ServerResponse<ReviewResultResponse>> review(@Valid @RequestBody ReviewRequest request) {
        return ServerResponses.ok(reviewApplyService.apply(
                request.getTargetType(), request.getTargetId(),
                request.getTargetStatus(), request.getReviewer()));
    }

    @PostMapping("/batch")
    @Operation(summary = "批次審核",
            description = "各筆可屬於不同資料類型；個別失敗不影響其他項目，逐筆回報成功或失敗原因。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次、不支援的目標類型或未提供審核者")
    })
    public ResponseEntity<ServerResponse<List<ReviewResultResponse>>> reviewBatch(
            @Valid @RequestBody BatchReviewRequest request) {

        return ServerResponses.ok(batchReviewService.applyBatch(request));
    }
}
