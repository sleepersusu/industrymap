package com.profetai.industrymap.controller;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.ReviewScopeQuery;
import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.item.CompositionResponse;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import com.profetai.industrymap.payloads.item.CreateItemAliasRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.ItemAliasResponse;
import com.profetai.industrymap.payloads.item.ItemResponse;
import com.profetai.industrymap.payloads.item.ResolveItemQuery;
import com.profetai.industrymap.payloads.supply.MarketShareQuery;
import com.profetai.industrymap.payloads.supply.MarketShareResponse;
import com.profetai.industrymap.payloads.supply.SupplierQuery;
import com.profetai.industrymap.payloads.supply.SupplierResponse;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品類節點的建立、別名、組成關係與反向查詢，以及該零件的供應公司與市佔率。
 *
 * <p>供應關係與市佔率掛在零件底下（第 2 層），符合 api-design.md 的巢狀深度限制。
 * 查詢條件一律以 payload 承載，controller 不逐個接 query 參數。</p>
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@Tag(name = "Item", description = "品類節點與供應關係")
public class ItemController {

    private final ItemService itemService;
    private final ItemCompositionService itemCompositionService;
    private final CompanyItemRoleService companyItemRoleService;
    private final MarketShareService marketShareService;

    @PostMapping
    @Operation(summary = "建立品類節點")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗"),
            @ApiResponse(responseCode = "409", description = "正規化名稱已存在")
    })
    public ResponseEntity<ServerResponse<ItemResponse>> create(@Valid @RequestBody CreateItemRequest request) {
        return ServerResponses.created(ItemResponse.from(itemService.create(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "取得品類節點")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "查無此節點")
    })
    public ResponseEntity<ServerResponse<ItemResponse>> get(@PathVariable Long id) {
        return ServerResponses.ok(ItemResponse.from(itemService.getVisibleById(id)));
    }

    @GetMapping
    @Operation(summary = "以名稱或別名解析品類節點",
            description = "寫入前先呼叫這支確認節點是否已存在，避免同義異名產生重複節點。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "400", description = "未提供名稱"),
            @ApiResponse(responseCode = "404", description = "名稱與別名皆查無")
    })
    public ResponseEntity<ServerResponse<ItemResponse>> resolveByName(@Valid @ModelAttribute ResolveItemQuery query) {
        ItemResponse resolved = itemService.resolveVisibleByName(query.getName())
                .map(ItemResponse::from)
                .orElseThrow(() -> new ServerException("查無此品類節點：" + query.getName(), HttpStatus.NOT_FOUND));
        return ServerResponses.ok(resolved);
    }

    @PostMapping("/{id}/aliases")
    @Operation(summary = "登記品類節點別名")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "404", description = "查無此節點"),
            @ApiResponse(responseCode = "409", description = "別名與其他節點名稱或既有別名衝突")
    })
    public ResponseEntity<ServerResponse<ItemAliasResponse>> addAlias(
            @PathVariable Long id, @Valid @RequestBody CreateItemAliasRequest request) {

        return ServerResponses.created(ItemAliasResponse.from(itemService.addAlias(id, request)));
    }

    @PostMapping("/{id}/components")
    @Operation(summary = "建立 part-of 組成關係",
            description = "寫入前於 service 層偵測循環；會造成循環的關係一律拒絕。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "未提供必要性或來源"),
            @ApiResponse(responseCode = "404", description = "查無上層或下層節點"),
            @ApiResponse(responseCode = "409", description = "關係已存在或會造成循環")
    })
    public ResponseEntity<ServerResponse<CompositionResponse>> addComponent(
            @PathVariable Long id, @Valid @RequestBody CreateCompositionRequest request) {

        return ServerResponses.created(CompositionResponse.from(itemCompositionService.create(id, request)));
    }

    @GetMapping("/{id}/compositions")
    @Operation(summary = "查節點的組成關係",
            description = "逐筆回傳上下層節點、必要性與審核狀態。組成樹回應只給節點 id，"
                    + "這支補上關係本身的定位資訊，讓組成關係也能經審核端點以自然鍵定位。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無資料時回空清單"),
            @ApiResponse(responseCode = "404", description = "查無此節點")
    })
    public ResponseEntity<ServerResponse<List<CompositionResponse>>> getCompositions(
            @PathVariable Long id, @Valid @ModelAttribute ReviewScopeQuery query) {

        return ServerResponses.ok(itemCompositionService.findCompositions(id, query.isIncludeDrafts()));
    }

    @GetMapping("/{id}/end-products")
    @Operation(summary = "反向查詢零件所屬的終端成品",
            description = "沿組成關係向上回溯，回傳所有可達的終端成品——某顆零件最後裝進了哪些產品。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "查無此節點")
    })
    public ResponseEntity<ServerResponse<List<ItemResponse>>> getEndProducts(
            @PathVariable Long id, @Valid @ModelAttribute ReviewScopeQuery query) {

        List<ItemResponse> endProducts =
                itemCompositionService.findReachableEndProducts(id, query.isIncludeDrafts()).stream()
                        .map(ItemResponse::from)
                        .toList();
        return ServerResponses.ok(endProducts);
    }

    @GetMapping("/{id}/suppliers")
    @Operation(summary = "查零件的供應公司", description = "可依角色過濾，例如只看代工組裝商。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無資料時回空清單"),
            @ApiResponse(responseCode = "400", description = "查詢條件驗證失敗")
    })
    public ResponseEntity<ServerResponse<List<SupplierResponse>>> getSuppliers(
            @PathVariable Long id, @Valid @ModelAttribute SupplierQuery query) {

        return ServerResponses.ok(companyItemRoleService.findSuppliers(id, query.getRole(), query.isIncludeDrafts()));
    }

    @GetMapping("/{id}/market-share")
    @Operation(summary = "查零件的市佔率排名",
            description = "依百分比降冪排序；同一組維度若有多個來源，各來源會各自成為一筆並附上來源明細。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無資料時回空清單而非 404"),
            @ApiResponse(responseCode = "400", description = "期間、地區或口徑缺漏")
    })
    public ResponseEntity<ServerResponse<List<MarketShareResponse>>> getMarketShare(
            @PathVariable Long id, @Valid @ModelAttribute MarketShareQuery query) {

        return ServerResponses.ok(marketShareService.findRanking(id, query.getPeriodType(), query.getPeriodValue(),
                query.getRegion(), query.getMetric(), query.isIncludeDrafts()));
    }
}
