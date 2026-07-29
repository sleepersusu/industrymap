package com.profetai.industrymap.controller;

import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.company.CompanyIdentifierResponse;
import com.profetai.industrymap.payloads.company.CompanyItemQuery;
import com.profetai.industrymap.payloads.company.CompanyItemResponse;
import com.profetai.industrymap.payloads.company.CompanyQuery;
import com.profetai.industrymap.payloads.company.CompanyResponse;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.service.company.CompanyService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 公司主檔、識別碼與別名。
 *
 * <p>路徑識別用公司代號的交易所限定形式（{@code TWSE:2330}），未上市公司則用正規化名稱——
 * 兩者都不是內部自增主鍵。代號只在發行它的交易所內唯一，因此路徑上必須帶類型。</p>
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Company", description = "公司主檔與識別碼")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyItemRoleService companyItemRoleService;

    @PostMapping
    @Operation(summary = "建立公司", description = "未上市公司可不帶任何識別碼，直接建立。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗"),
            @ApiResponse(responseCode = "409", description = "正規化名稱已存在")
    })
    public ResponseEntity<ServerResponse<CompanyResponse>> create(@Valid @RequestBody CreateCompanyRequest request) {
        Company company = companyService.create(request);
        return ServerResponses.created(CompanyResponse.from(company, List.of()));
    }

    @GetMapping
    @Operation(summary = "列出公司",
            description = "產業地圖從公司側進入的入口：呼叫端不需要事先知道任何代號。"
                    + "名稱關鍵字會正規化後同時比對公司名稱與別名（以 TSMC 查得到台積電），"
                    + "並可依國別、公開發行狀態、供應的品類節點與供應角色過濾，條件可併用。"
                    + "角色可單獨使用以跨零件彙總（例如列出所有代工組裝廠），與品類節點併用時"
                    + "則收斂為「對該零件具有該角色」。已駁回的公司任何條件下都不外露。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無符合資料時回空清單與總筆數 0，而非 404"),
            @ApiResponse(responseCode = "400", description = "查詢條件驗證失敗"),
            @ApiResponse(responseCode = "404", description = "指定的品類節點不存在")
    })
    public ResponseEntity<ServerResponse<PageResponse<CompanyResponse>>> list(
            @Valid @ModelAttribute CompanyQuery query) {

        return ServerResponses.ok(companyService.findCompanies(query));
    }

    @GetMapping("/{code}")
    @Operation(summary = "依代號取得公司",
            description = "回傳公司資料與其所有識別碼。代號用交易所限定形式 `<類型>:<代號值>`，"
                    + "未上市公司以正規化名稱作為代號位置的識別。裸代號仍可查，但同代號值跨交易所撞號時回 409。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "查無此代號"),
            @ApiResponse(responseCode = "409", description = "裸代號對應多家公司，須改用限定形式")
    })
    public ResponseEntity<ServerResponse<CompanyResponse>> get(
            @Parameter(description = "公司對外識別，例：TWSE:2330；未上市公司用正規化名稱",
                    example = "TWSE:2330") @PathVariable String code) {

        Company company = companyService.getVisibleByReference(code);
        return ServerResponses.ok(
                CompanyResponse.from(company, companyService.findVisibleIdentifiers(company.getId())));
    }

    @GetMapping("/{code}/items")
    @Operation(summary = "列出公司供應的零件",
            description = "從公司側往下走的入口：知道一家公司就能列出它供應的品類節點，並繼續展開。"
                    + "同一個節點只出現一筆，該公司對它的所有角色收在 roles 內——"
                    + "一家公司對同一顆晶片可以同時是製造與封測。可依角色過濾。"
                    + "已駁回的公司視為不存在；已駁回的角色、以及指向已駁回節點的角色都不外露。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功；無供應角色時回空清單而非 404"),
            @ApiResponse(responseCode = "400", description = "查詢條件驗證失敗"),
            @ApiResponse(responseCode = "404", description = "查無此公司"),
            @ApiResponse(responseCode = "409", description = "裸代號對應多家公司，須改用限定形式")
    })
    public ResponseEntity<ServerResponse<List<CompanyItemResponse>>> getSuppliedItems(
            @Parameter(description = "公司對外識別，例：TWSE:2330；未上市公司用正規化名稱",
                    example = "TWSE:2330") @PathVariable String code,
            @Valid @ModelAttribute CompanyItemQuery query) {

        Company company = companyService.getVisibleByReference(code);
        return ServerResponses.ok(companyItemRoleService.findSuppliedItems(
                company.getId(), query.getRole(), query.isIncludeDrafts()));
    }

    @PostMapping("/{code}/identifiers")
    @Operation(summary = "登記公司識別碼", description = "同一公司可有多筆識別碼，但至多一筆主要識別碼。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "404", description = "查無此公司"),
            @ApiResponse(responseCode = "409",
                    description = "識別碼已被登記、該公司已有主要識別碼，或裸代號對應多家公司")
    })
    public ResponseEntity<ServerResponse<CompanyIdentifierResponse>> addIdentifier(
            @Parameter(description = "公司對外識別，例：TWSE:2330；未上市公司用正規化名稱",
                    example = "TWSE:2330") @PathVariable String code,
            @Valid @RequestBody CreateIdentifierRequest request) {

        Company company = companyService.getByReference(code);
        return ServerResponses.created(
                CompanyIdentifierResponse.from(companyService.addIdentifier(company.getId(), request)));
    }

    @PostMapping("/{code}/aliases")
    @Operation(summary = "登記公司別名")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "404", description = "查無此公司"),
            @ApiResponse(responseCode = "409",
                    description = "別名與其他公司名稱或既有別名衝突，或裸代號對應多家公司")
    })
    public ResponseEntity<ServerResponse<String>> addAlias(
            @Parameter(description = "公司對外識別，例：TWSE:2330；未上市公司用正規化名稱",
                    example = "TWSE:2330") @PathVariable String code,
            @Valid @RequestBody CreateCompanyAliasRequest request) {

        Company company = companyService.getByReference(code);
        return ServerResponses.created(companyService.addAlias(company.getId(), request).getNormalizedAlias());
    }
}
