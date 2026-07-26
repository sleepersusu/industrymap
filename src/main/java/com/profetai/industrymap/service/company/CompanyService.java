package com.profetai.industrymap.service.company;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.CompanyReferences;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 公司主檔、識別碼與別名。
 *
 * <p>未上市公司必須能正常建立——代號一律走 {@link CompanyIdentifier}，
 * 公司本體不因缺少代號而無法登錄（design D5）。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;
    private final CompanyIdentifierRepository companyIdentifierRepository;

    /**
     * 建立公司。
     *
     * @throws ServerException 正規化名稱與既有公司重複（409）
     */
    @Transactional
    public Company create(CreateCompanyRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        String normalizedName = NameNormalizer.normalize(request.getDisplayName());
        if (companyRepository.existsByNormalizedName(normalizedName)) {
            throw new ServerException("已存在相同名稱的公司：" + normalizedName, HttpStatus.CONFLICT);
        }

        Company company = Company.builder()
                .normalizedName(normalizedName)
                .displayName(request.getDisplayName())
                .country(request.getCountry())
                .isPublic(request.isPublicCompany())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        Company saved = companyRepository.save(company);
        log.info("建立公司 companyId={} normalizedName={}", saved.getId(), normalizedName);
        return saved;
    }

    /**
     * 登記公司識別碼。
     *
     * @throws ServerException 公司不存在（404）、（類型, 代號值）已被登記或該公司已有主要識別碼（409）
     */
    @Transactional
    public CompanyIdentifier addIdentifier(Long companyId, CreateIdentifierRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        Company company = getById(companyId);

        if (companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(
                request.getIdentifierType(), request.getIdentifierValue())) {
            throw new ServerException("識別碼已被登記："
                    + request.getIdentifierType() + " " + request.getIdentifierValue(), HttpStatus.CONFLICT);
        }
        // 主要識別碼決定日後抓股價要打哪個市場，因此每家公司只能有一筆
        if (request.isPrimary() && companyIdentifierRepository.existsByCompanyIdAndIsPrimaryTrue(companyId)) {
            throw new ServerException("該公司已有主要識別碼：" + companyId, HttpStatus.CONFLICT);
        }

        CompanyIdentifier identifier = CompanyIdentifier.builder()
                .company(company)
                .identifierType(request.getIdentifierType())
                .identifierValue(request.getIdentifierValue())
                .isPrimary(request.isPrimary())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        CompanyIdentifier saved = companyIdentifierRepository.save(identifier);
        log.info("登記公司識別碼 companyId={} type={} value={} primary={}",
                companyId, request.getIdentifierType(), request.getIdentifierValue(), request.isPrimary());
        return saved;
    }

    /**
     * 登記公司別名。與品類別名同理，除了別名表本身的唯一鍵，
     * 還要擋掉「別名等於另一家公司的正規化名稱」這種跨表衝突。
     *
     * @throws ServerException 公司不存在（404）、別名與其他公司名稱或既有別名衝突（409）
     */
    @Transactional
    public CompanyAlias addAlias(Long companyId, CreateCompanyAliasRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        Company company = getById(companyId);
        String normalizedAlias = NameNormalizer.normalize(request.getAlias());

        boolean collidesWithAnotherCompany = companyRepository.findByNormalizedName(normalizedAlias)
                .filter(existing -> !existing.getId().equals(company.getId()))
                .isPresent();
        if (collidesWithAnotherCompany) {
            throw new ServerException("別名與另一家公司的名稱衝突：" + normalizedAlias, HttpStatus.CONFLICT);
        }
        if (companyAliasRepository.existsByNormalizedAlias(normalizedAlias)) {
            throw new ServerException("別名已被登記：" + normalizedAlias, HttpStatus.CONFLICT);
        }

        CompanyAlias alias = CompanyAlias.builder()
                .company(company)
                .normalizedAlias(normalizedAlias)
                .displayAlias(request.getAlias())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        CompanyAlias saved = companyAliasRepository.save(alias);
        log.info("登記公司別名 companyId={} alias={}", companyId, normalizedAlias);
        return saved;
    }

    /**
     * 依代號查公司——對外 API 以代號為路徑識別，不曝露內部自增主鍵。
     *
     * @throws ServerException 查無此代號（404）
     */
    @Transactional(readOnly = true)
    public Company getByIdentifierValue(String identifierValue) {
        List<CompanyIdentifier> identifiers = companyIdentifierRepository.findByIdentifierValue(identifierValue);
        if (identifiers.isEmpty()) {
            throw new ServerException("查無此公司代號：" + identifierValue, HttpStatus.NOT_FOUND);
        }
        return initialized(identifiers.get(0));
    }

    /**
     * 以對外路徑識別取得公司：先當作代號查，查不到再當作公司名稱查。
     *
     * <p>對外 API 一律不曝露內部自增主鍵，但未上市公司沒有任何代號，
     * 因此以正規化名稱作為它們的路徑識別。</p>
     *
     * @throws ServerException 代號與名稱都查無（404）
     */
    @Transactional(readOnly = true)
    public Company getByReference(String reference) {
        List<CompanyIdentifier> identifiers = companyIdentifierRepository.findByIdentifierValue(reference);
        if (!identifiers.isEmpty()) {
            return initialized(identifiers.get(0));
        }
        return companyRepository.findByNormalizedName(NameNormalizer.normalize(reference))
                .orElseThrow(() -> new ServerException("查無此公司：" + reference, HttpStatus.NOT_FOUND));
    }

    /**
     * 取出識別碼所屬的公司本體。
     *
     * <p>{@code CompanyIdentifier.company} 是 LAZY 關聯，直接回傳拿到的是未初始化的代理；
     * open-in-view 為 false，呼叫端在交易外讀欄位時會拋 LazyInitializationException。
     * 因此改以主鍵重新載入實體——讀代理的主鍵不會觸發初始化，這一步是安全的。</p>
     */
    private Company initialized(CompanyIdentifier identifier) {
        Long companyId = identifier.getCompany().getId();
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ServerException("查無此公司：" + companyId, HttpStatus.NOT_FOUND));
    }

    /** 以名稱或別名解析既有公司，供寫入前去重使用（design D9） */
    @Transactional(readOnly = true)
    public Optional<Company> resolveByName(String rawName) {
        String normalized = NameNormalizer.normalize(rawName);
        return companyRepository.findByNormalizedName(normalized)
                .or(() -> companyAliasRepository.findByNormalizedAlias(normalized).map(CompanyAlias::getCompany));
    }

    /** 取得公司，查無則 404 */
    @Transactional(readOnly = true)
    public Company getById(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ServerException("查無此公司：" + companyId, HttpStatus.NOT_FOUND));
    }

    /** 取得公司的所有識別碼。供寫入流程使用，不過濾審核狀態 */
    @Transactional(readOnly = true)
    public List<CompanyIdentifier> findIdentifiers(Long companyId) {
        return companyIdentifierRepository.findByCompanyId(companyId);
    }

    /**
     * 對外依路徑識別取得公司：已駁回的公司視為不存在。
     *
     * <p>與 {@link #getByReference} 分開是因為寫入流程（登記識別碼、別名、建立供應關係）
     * 仍需解析公司實體，而對外查詢不得回傳已駁回的資料。</p>
     *
     * @throws ServerException 代號與名稱都查無，或該公司已被駁回（404）
     */
    @Transactional(readOnly = true)
    public Company getVisibleByReference(String reference) {
        Company company = getByReference(reference);
        if (!ReviewScopes.isExposable(company.getReviewStatus())) {
            throw new ServerException("查無此公司：" + reference, HttpStatus.NOT_FOUND);
        }
        return company;
    }

    /**
     * 取得單一公司的對外識別（design D4）：優先主要識別碼的代號，無識別碼才退回正規化名稱。
     * 規則本身寫在 {@link CompanyReferences}，本方法只負責把識別碼查出來。
     */
    @Transactional(readOnly = true)
    public String referenceOf(Company company) {
        return CompanyReferences.of(company, companyIdentifierRepository.findByCompanyId(company.getId()));
    }

    /**
     * 批次取得多家公司的對外識別，回傳 companyId → 對外識別。
     *
     * <p>供應商清單與市佔率排名要逐筆組出對外識別，逐家查識別碼會變成 N+1，
     * 因此一次把主要識別碼撈齊再比對。</p>
     */
    @Transactional(readOnly = true)
    public Map<Long, String> referencesOf(Collection<Company> companies) {
        Map<Long, Company> distinctCompanies = new LinkedHashMap<>();
        for (Company company : companies) {
            distinctCompanies.putIfAbsent(company.getId(), company);
        }
        if (distinctCompanies.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<CompanyIdentifier>> primaryByCompanyId =
                companyIdentifierRepository.findByCompanyIdInAndIsPrimaryTrue(distinctCompanies.keySet()).stream()
                        // company 是 LAZY 關聯，但只讀主鍵不會觸發初始化，這一步是安全的
                        .collect(Collectors.groupingBy(identifier -> identifier.getCompany().getId()));

        Map<Long, String> references = new LinkedHashMap<>();
        distinctCompanies.forEach((companyId, company) -> references.put(companyId,
                CompanyReferences.of(company, primaryByCompanyId.getOrDefault(companyId, List.of()))));
        return references;
    }

    /** 對外列出公司識別碼：已駁回的識別碼不外露 */
    @Transactional(readOnly = true)
    public List<CompanyIdentifier> findVisibleIdentifiers(Long companyId) {
        return findIdentifiers(companyId).stream()
                .filter(identifier -> ReviewScopes.isExposable(identifier.getReviewStatus()))
                .toList();
    }
}
