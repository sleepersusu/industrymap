package com.profetai.industrymap.controller;

import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.payloads.ServerResponse;
import com.profetai.industrymap.payloads.ServerResponses;
import com.profetai.industrymap.payloads.company.CompanyIdentifierResponse;
import com.profetai.industrymap.payloads.company.CompanyResponse;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.service.company.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公司主檔、識別碼與別名。
 *
 * <p>路徑識別用公司代號，未上市公司則用正規化名稱——兩者都不是內部自增主鍵。</p>
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Company", description = "公司主檔與識別碼")
public class CompanyController {

    private final CompanyService companyService;

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

    @GetMapping("/{code}")
    @Operation(summary = "依代號取得公司", description = "回傳公司資料與其所有識別碼；未上市公司以正規化名稱作為代號位置的識別。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "查無此代號")
    })
    public ResponseEntity<ServerResponse<CompanyResponse>> get(
            @Parameter(description = "公司代號，例：2330；未上市公司用正規化名稱") @PathVariable String code) {

        Company company = companyService.getVisibleByReference(code);
        return ServerResponses.ok(
                CompanyResponse.from(company, companyService.findVisibleIdentifiers(company.getId())));
    }

    @PostMapping("/{code}/identifiers")
    @Operation(summary = "登記公司識別碼", description = "同一公司可有多筆識別碼，但至多一筆主要識別碼。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "404", description = "查無此公司"),
            @ApiResponse(responseCode = "409", description = "識別碼已被登記或該公司已有主要識別碼")
    })
    public ResponseEntity<ServerResponse<CompanyIdentifierResponse>> addIdentifier(
            @PathVariable String code, @Valid @RequestBody CreateIdentifierRequest request) {

        Company company = companyService.getByReference(code);
        return ServerResponses.created(
                CompanyIdentifierResponse.from(companyService.addIdentifier(company.getId(), request)));
    }

    @PostMapping("/{code}/aliases")
    @Operation(summary = "登記公司別名")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "建立成功"),
            @ApiResponse(responseCode = "404", description = "查無此公司"),
            @ApiResponse(responseCode = "409", description = "別名與其他公司名稱或既有別名衝突")
    })
    public ResponseEntity<ServerResponse<String>> addAlias(
            @PathVariable String code, @Valid @RequestBody CreateCompanyAliasRequest request) {

        Company company = companyService.getByReference(code);
        return ServerResponses.created(companyService.addAlias(company.getId(), request).getNormalizedAlias());
    }
}
