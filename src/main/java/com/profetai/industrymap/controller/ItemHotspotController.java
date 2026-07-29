package com.profetai.industrymap.controller;

import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.item.AmendHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.HotspotResponse;
import com.profetai.industrymap.service.item.ItemImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 熱區的寫入端點。查詢端點掛在節點底下（{@code GET /api/items/{id}/images}），
 * 因為使用者都是從「這個節點的圖長什麼樣」出發。
 *
 * <p>熱區自成一個前綴而非再往圖片底下巢狀：{@code /api/items/{id}/images} 已經是第二層，
 * 再掛一層就超出既有的巢狀深度限制。所屬圖片改由 request body 指定，
 * 與 {@code /api/supply-relations} 以 body 帶公司代號同一套作法。</p>
 *
 * <p>刻意沒有 {@code DELETE}（design D7）：拿掉一個畫錯的熱區走審核駁回，
 * 已駁回的熱區不再出現於任何對外查詢，效果等同移除，且留下了軌跡。</p>
 */
@RestController
@RequestMapping("/api/item-hotspots")
@RequiredArgsConstructor
@Tag(name = "ItemHotspot", description = "圖片熱區寫入")
public class ItemHotspotController {

    private final ItemImageService itemImageService;

    @PostMapping
    @Operation(summary = "在圖片上標記熱區",
            description = "座標為 0–1 的相對比例、至少三點。同一張圖上可以有多個熱區指向同一個節點"
                    + "（前煞車／後煞車），以位置標籤區分；同一張圖的位置標籤不得重複。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "位置標籤缺漏或座標不合法"),
            @ApiResponse(responseCode = "404", description = "查無此圖片或熱區指向的節點"),
            @ApiResponse(responseCode = "409", description = "此圖片已有同一位置標籤的熱區")
    })
    public ResponseEntity<ServerResponse<HotspotResponse>> create(
            @Valid @RequestBody CreateHotspotRequest request) {

        return ServerResponses.created(HotspotResponse.from(itemImageService.createHotspot(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修正熱區",
            description = "全量替換位置標籤與座標，兩者都必須帶齊——座標是一整組點，部分更新無法表達。"
                    + "內容實際變更後審核狀態退回草稿，需重新審核才對外可見；"
                    + "送出與現況完全相同的值視為無變更，不改狀態。"
                    + "改指向哪個節點請改以建立新熱區並駁回舊的表達。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修正成功"),
            @ApiResponse(responseCode = "400", description = "位置標籤缺漏或座標不合法"),
            @ApiResponse(responseCode = "404", description = "查無此熱區"),
            @ApiResponse(responseCode = "409", description = "位置標籤與同一張圖的其他熱區衝突")
    })
    public ResponseEntity<ServerResponse<HotspotResponse>> amend(
            @PathVariable Long id, @Valid @RequestBody AmendHotspotRequest request) {

        return ServerResponses.ok(HotspotResponse.from(itemImageService.amendHotspot(id, request)));
    }
}
