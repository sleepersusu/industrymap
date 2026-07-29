package com.profetai.industrymap.controller;

import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.bulk.BatchCompositionItem;
import com.profetai.industrymap.payloads.bulk.BatchCreateRequest;
import com.profetai.industrymap.payloads.bulk.BatchCreateResultResponse;
import com.profetai.industrymap.payloads.bulk.BatchIdentifierItem;
import com.profetai.industrymap.payloads.bulk.BatchItemImageItem;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.service.bulk.BulkAuthoringService;
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
 * 內容資料的批次建立（design D3）。
 *
 * <p>收斂在單一前綴而非散在各資源底下：批次的實際用途是「一次匯入一台腳踏車的全部資料」，
 * 呼叫端會依序打完六種類型，放在一起比較好找，也不必為了批次去動既有資源的路徑結構。</p>
 *
 * <p>個別項目失敗不影響其他項目，因此整體一律回 200 並逐筆回報；成功項目帶自然鍵，
 * 可直接組成 {@code POST /api/reviews/batch} 的請求。</p>
 */
@RestController
@RequestMapping("/api/bulk")
@RequiredArgsConstructor
@Tag(name = "BulkAuthoring", description = "內容資料的批次建立")
public class BulkAuthoringController {

    private final BulkAuthoringService bulkAuthoringService;

    @PostMapping("/items")
    @Operation(summary = "批次建立品類節點",
            description = "逐筆建立並回報結果；名稱重複的那筆回報 409，其餘照常建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createItems(
            @Valid @RequestBody BatchCreateRequest<CreateItemRequest> request) {

        return ServerResponses.ok(bulkAuthoringService.createItems(request.getItems()));
    }

    @PostMapping("/compositions")
    @Operation(summary = "批次建立 part-of 組成關係",
            description = "各筆自帶上層節點；會造成循環或已存在的關係回報 409，其餘照常建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createCompositions(
            @Valid @RequestBody BatchCreateRequest<BatchCompositionItem> request) {

        return ServerResponses.ok(bulkAuthoringService.createCompositions(request.getItems()));
    }

    @PostMapping("/companies")
    @Operation(summary = "批次建立公司", description = "未上市公司可不帶任何識別碼，直接建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createCompanies(
            @Valid @RequestBody BatchCreateRequest<CreateCompanyRequest> request) {

        return ServerResponses.ok(bulkAuthoringService.createCompanies(request.getItems()));
    }

    @PostMapping("/identifiers")
    @Operation(summary = "批次登記公司識別碼", description = "各筆自帶公司代號；每家公司至多一筆主要識別碼。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createIdentifiers(
            @Valid @RequestBody BatchCreateRequest<BatchIdentifierItem> request) {

        return ServerResponses.ok(bulkAuthoringService.createIdentifiers(request.getItems()));
    }

    @PostMapping("/supply-roles")
    @Operation(summary = "批次建立公司與零件的供應角色",
            description = "同一組公司零件可有多個角色，同角色重複才算衝突。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createSupplyRoles(
            @Valid @RequestBody BatchCreateRequest<CreateCompanyItemRoleRequest> request) {

        return ServerResponses.ok(bulkAuthoringService.createSupplyRoles(request.getItems()));
    }

    @PostMapping("/item-images")
    @Operation(summary = "批次建立節點圖片",
            description = "各筆自帶所屬節點；同一節點的視角標籤重複的那筆回報 409，其餘照常建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createItemImages(
            @Valid @RequestBody BatchCreateRequest<BatchItemImageItem> request) {

        return ServerResponses.ok(bulkAuthoringService.createItemImages(request.getItems()));
    }

    @PostMapping("/item-hotspots")
    @Operation(summary = "批次建立圖片熱區",
            description = "熱區的自然批次單位是一張圖——標記一張爆炸圖會一次產生數十個熱區。"
                    + "座標不合法的那筆回報 400、位置標籤重複的那筆回報 409，其餘照常建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createHotspots(
            @Valid @RequestBody BatchCreateRequest<CreateHotspotRequest> request) {

        return ServerResponses.ok(bulkAuthoringService.createHotspots(request.getItems()));
    }

    @PostMapping("/market-shares")
    @Operation(summary = "批次寫入市佔率",
            description = "不同來源對同一組維度的數值可並存，同來源重複才算衝突。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "批次處理完成，逐筆回報結果"),
            @ApiResponse(responseCode = "400", description = "空批次或欄位驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<BatchCreateResultResponse>>> createMarketShares(
            @Valid @RequestBody BatchCreateRequest<CreateMarketShareRequest> request) {

        return ServerResponses.ok(bulkAuthoringService.createMarketShares(request.getItems()));
    }
}
