package com.profetai.industrymap.controller;

import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.item.ComponentNode;
import com.profetai.industrymap.payloads.item.ComponentTreeQuery;
import com.profetai.industrymap.payloads.item.EndProductQuery;
import com.profetai.industrymap.payloads.item.ItemResponse;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 以產品為起點的組成查詢。產品與零件在資料模型上是同一種實體（design D1），
 * 這裡的路徑只是使用者視角的入口。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "產品組成查詢")
public class ProductController {

    private final ItemCompositionService itemCompositionService;
    private final ItemService itemService;

    @GetMapping
    @Operation(summary = "列出終端成品",
            description = "產業地圖的進入點：呼叫端不需要事先知道任何 id，就能取得可以往下展開的產品清單。"
                    + "可依名稱關鍵字模糊搜尋；已駁回的節點任何條件下都不外露。"
                    + "只列終端成品，不列零件節點。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無符合資料時回空清單與總筆數 0，而非 404"),
            @ApiResponse(responseCode = "400", description = "查詢條件驗證失敗")
    })
    public ResponseEntity<ServerResponse<PageResponse<ItemResponse>>> listEndProducts(
            @Valid @ModelAttribute EndProductQuery query) {

        return ServerResponses.ok(itemService.findEndProducts(query));
    }

    @GetMapping("/{id}/components")
    @Operation(summary = "展開產品組成樹",
            description = "沿 part-of 關係展開指定層數的組成樹；不含 is-a 細分類型。深度上限 5，避免一次拉出整張圖。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "400", description = "查詢條件驗證失敗"),
            @ApiResponse(responseCode = "404", description = "查無此節點")
    })
    public ResponseEntity<ServerResponse<ComponentNode>> getComponents(
            @PathVariable Long id, @Valid @ModelAttribute ComponentTreeQuery query) {

        return ServerResponses.ok(itemCompositionService.expandTree(
                id, query.getDepth(), query.getNecessity(), query.isIncludeDrafts()));
    }
}
