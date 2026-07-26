package com.profetai.industrymap.service.review;

import com.profetai.industrymap.payloads.review.BatchReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.payloads.review.ReviewTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 批次審核（design D2）：逐筆回報，不整批 rollback。
 *
 * <p>刻意獨立成一個 bean 而非把迴圈寫進 {@link ReviewApplyService}——同一個 bean 內的自我呼叫
 * 不會經過交易代理，所有項目會落在同一個交易裡，一筆失敗就把其餘全部撤銷，
 * 正好是這裡要避免的行為。本層不開交易，交易邊界留在被呼叫的單筆審核上。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BatchReviewService {

    private final ReviewApplyService reviewApplyService;

    /**
     * 逐筆套用審核；單筆失敗只記錄該筆原因，其餘照常進行。
     * 各筆的定位方式互不相干，同一批可混用內部 id 與自然鍵（design D1）。
     */
    public List<ReviewResultResponse> applyBatch(BatchReviewRequest request) {
        List<ReviewResultResponse> results = new ArrayList<>(request.getTargets().size());

        for (ReviewTarget target : request.getTargets()) {
            try {
                results.add(reviewApplyService.apply(target.getTargetType(), target.getTargetId(),
                        target.getNaturalKey(), request.getTargetStatus(), request.getReviewer()));
            } catch (Exception ex) {
                // 一筆識別碼打錯，不該讓另外 99 筆已經看過的資料白審。
                // 這裡是批次迴圈的邊界層，攔截範圍刻意涵蓋非業務例外：單筆交易 commit 期的
                // 鎖競爭等失敗拋的不是 ServerException，只攔 ServerException 會讓剩餘項目
                // 全部不處理、呼叫端也拿不到逐筆結果，正好違反本端點的逐筆回報語意。
                log.warn("批次審核單筆失敗 targetType={} targetId={} reason={}",
                        target.getTargetType(), target.getTargetId(), ex.getMessage(), ex);
                results.add(ReviewResultResponse.failure(
                        target.getTargetType(), target.getTargetId(), ex.getMessage()));
            }
        }

        long failed = results.stream().filter(result -> !result.isSuccess()).count();
        log.info("批次審核完成 total={} failed={} status={}",
                results.size(), failed, request.getTargetStatus());
        return results;
    }
}
