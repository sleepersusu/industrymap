package com.profetai.industrymap.controller;

import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.payloads.supply.MarketShareResponse;
import com.profetai.industrymap.payloads.supply.SupplierResponse;
import com.profetai.industrymap.service.company.CompanyService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
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

/**
 * 供應關係與市佔率的寫入端點。查詢端點掛在零件底下（見 {@link ItemController}），
 * 因為使用者都是從「這個零件是誰做的」出發。
 */
@RestController
@RequestMapping("/api/supply-relations")
@RequiredArgsConstructor
@Tag(name = "SupplyRelation", description = "供應關係與市佔率寫入")
public class SupplyRelationController {

    private final CompanyItemRoleService companyItemRoleService;
    private final MarketShareService marketShareService;
    /** 建立後的回應同樣要給出統一規則下的公司對外識別（design D4），因此需要解析主要代號 */
    private final CompanyService companyService;

    @PostMapping("/roles")
    @Operation(summary = "建立公司與零件的供應關係",
            description = "公司以代號指定（未上市公司用正規化名稱），與建立公司回應的 reference 一致。"
                    + "同一組公司零件可有多個角色（設計、製造、封測），同角色重複才算衝突。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗"),
            @ApiResponse(responseCode = "404", description = "查無此公司代號或零件"),
            @ApiResponse(responseCode = "409", description = "同組公司零件角色已存在")
    })
    public ResponseEntity<ServerResponse<SupplierResponse>> createRole(
            @Valid @RequestBody CreateCompanyItemRoleRequest request) {

        CompanyItemRole role = companyItemRoleService.create(request);
        return ServerResponses.created(
                SupplierResponse.from(role, companyService.referenceOf(role.getCompany())));
    }

    @PostMapping("/market-shares")
    @Operation(summary = "寫入市佔率",
            description = "公司以代號指定。期間、地區、口徑為必填；"
                    + "不同來源對同一組維度的不同數值可並存，同來源重複則衝突。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "維度缺漏或百分比超出 0–100"),
            @ApiResponse(responseCode = "404", description = "查無此公司代號或零件"),
            @ApiResponse(responseCode = "409", description = "同一來源已寫過相同維度")
    })
    public ResponseEntity<ServerResponse<MarketShareResponse>> createMarketShare(
            @Valid @RequestBody CreateMarketShareRequest request) {

        MarketShare marketShare = marketShareService.create(request);
        return ServerResponses.created(
                MarketShareResponse.from(marketShare, companyService.referenceOf(marketShare.getCompany())));
    }
}
