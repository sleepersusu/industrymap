package com.profetai.industrymap.service.company;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.CompanyReferences;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.company.CompanyQuery;
import com.profetai.industrymap.payloads.company.CompanyResponse;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemRepository;
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
import java.util.Set;
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
    private final ItemRepository itemRepository;

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
     * 接受限定形式（{@code TWSE:2330}）與裸代號值兩種寫法，規則同 {@link #getByReference}。
     *
     * @throws ServerException 查無此代號（404）、裸代號對應多家公司（409）
     */
    @Transactional(readOnly = true)
    public Company getByIdentifierValue(String identifierValue) {
        return resolveByIdentifier(identifierValue, false)
                .orElseThrow(() -> new ServerException("查無此公司代號：" + identifierValue, HttpStatus.NOT_FOUND));
    }

    /**
     * 以對外路徑識別取得公司：先當作代號查，查不到再當作公司名稱查。
     *
     * <p>對外 API 一律不曝露內部自增主鍵，但未上市公司沒有任何代號，
     * 因此以正規化名稱作為它們的路徑識別。</p>
     *
     * @throws ServerException 代號與名稱都查無（404）、裸代號對應多家公司（409）
     */
    @Transactional(readOnly = true)
    public Company getByReference(String reference) {
        return resolveByIdentifier(reference, false)
                .orElseGet(() -> byNormalizedName(reference));
    }

    private Company byNormalizedName(String reference) {
        return companyRepository.findByNormalizedName(NameNormalizer.normalize(reference))
                .orElseThrow(() -> new ServerException("查無此公司：" + reference, HttpStatus.NOT_FOUND));
    }

    /**
     * 以代號定位公司，查無則回空（交給呼叫端決定退回名稱查詢或報 404）。
     *
     * <p>限定形式走 {@code (類型, 代號值)} 唯一鍵，至多命中一筆。裸代號沿用值查詢，
     * 但<b>指向多家公司時不得任選一筆</b>——代號只在發行它的交易所內唯一，
     * 取 {@code get(0)} 等於讓資料庫回傳順序決定是哪一家公司，錯得無聲無息。</p>
     *
     * <p>歧義以<b>公司家數</b>而非識別碼筆數判定：同一家公司可在兩個交易所持有相同代號值
     * （上櫃轉上市會保留原代號），那不是歧義，回傳同一家公司即可。</p>
     *
     * <p>已駁回的識別碼一律不納入（design D8）：它不外露於任何回應，因此既不該是可用的定位手段，
     * 更不該讓一筆被駁回的誤建資料撞掉合法公司的裸代號查詢。</p>
     *
     * <p>{@code exposableCompaniesOnly} 讓歧義的判定範圍跟著呼叫端走：對外查詢看不到已駁回的公司，
     * 那些公司就不該讓它的請求變成歧義，也不該出現在候選清單裡——列出去的候選重送必須會成功。
     * 寫入流程則相反，必須看得見已駁回的公司，否則審核端點無法以代號把它改回草稿。</p>
     *
     * <p>限定形式<b>查無該筆</b>時才往下試裸代號：識別碼值本身有可能長得像限定形式
     * （{@code OTHER} 底下自訂的值），此時整個字串才是它的代號值。但該筆存在卻已駁回時
     * 一律停在這裡回空——呼叫端已明確指名交易所，再往下走會命中某個字面值恰好長得像限定形式的
     * 別家識別碼，而且是靜默的。</p>
     *
     * @throws ServerException 裸代號對應多家公司（409）
     */
    private Optional<Company> resolveByIdentifier(String reference, boolean exposableCompaniesOnly) {
        Optional<CompanyIdentifier> qualifiedMatch = CompanyReferences.parse(reference)
                .flatMap(qualified -> companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(
                        qualified.getIdentifierType(), qualified.getIdentifierValue()));
        if (qualifiedMatch.isPresent()) {
            return qualifiedMatch
                    .filter(identifier -> ReviewScopes.isExposable(identifier.getReviewStatus()))
                    .map(CompanyIdentifier::getCompany);
        }

        List<CompanyIdentifier> identifiers = companyIdentifierRepository.findByIdentifierValue(reference).stream()
                .filter(identifier -> ReviewScopes.isExposable(identifier.getReviewStatus()))
                .toList();
        if (exposableCompaniesOnly && distinctCompanyIds(identifiers).size() > 1) {
            // 單一命中時公司的審核狀態由呼叫端自己把關：在這裡濾掉會讓解析退回名稱查詢，
            // 恰好有公司以該代號值為正規化名稱時就會靜默回錯公司
            identifiers = withoutRejectedCompanies(identifiers);
        }

        if (distinctCompanyIds(identifiers).size() > 1) {
            String candidates = identifiers.stream()
                    .map(CompanyReferences::qualify)
                    .sorted()
                    .collect(Collectors.joining("、"));
            throw new ServerException("代號 " + reference + " 對應多家公司，請改用限定形式指定：" + candidates,
                    HttpStatus.CONFLICT);
        }
        return identifiers.stream().findFirst().map(CompanyIdentifier::getCompany);
    }

    private List<Long> distinctCompanyIds(Collection<CompanyIdentifier> identifiers) {
        return identifiers.stream()
                .map(identifier -> identifier.getCompany().getId())
                .distinct()
                .toList();
    }

    /**
     * 濾掉所屬公司已被駁回的識別碼。
     *
     * <p>審核是逐列套用的，公司被駁回時它的識別碼未必一併駁回，因此只看識別碼的狀態擋不住
     * 「對外根本不存在的公司」被算進歧義，也擋不住它被寫進 409 的候選清單。</p>
     */
    private List<CompanyIdentifier> withoutRejectedCompanies(List<CompanyIdentifier> identifiers) {
        return identifiers.stream()
                .filter(identifier -> ReviewScopes.isExposable(identifier.getCompany().getReviewStatus()))
                .toList();
    }

    /**
     * 公司列表（design D1–D5）：分頁、名稱與別名關鍵字、國別、公開發行狀態、供應零件皆可併用。
     *
     * <p>指定的品類節點不存在時回 404 而非空清單：呼叫端傳了一個根本不存在的 id，
     * 回空清單會讓它以為「這個零件沒有供應商」，與事實相反。條件都成立但查無資料時
     * 才回空清單與總筆數 0。</p>
     *
     * @throws ServerException 指定的品類節點不存在（404）
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> findCompanies(CompanyQuery query) {
        if (query.getItemId() != null && !itemRepository.existsById(query.getItemId())) {
            throw new ServerException("查無此品類節點：" + query.getItemId(), HttpStatus.NOT_FOUND);
        }

        Collection<String> reviewStatuses = ReviewScopes.visibleStatusNames(query.isIncludeDrafts());
        String namePattern = toNamePattern(query.getName());
        String country = blankToNull(query.getCountry());
        String companyRole = query.getCompanyRole() == null ? null : query.getCompanyRole().name();

        // 位移量以 long 計算：page 沒有上限，int 相乘會在大頁碼時溢位成負數，
        // 送進 SQL 就變成負的 OFFSET，資料庫直接報錯而非回空頁
        long offset = (long) query.getPage() * query.getSize();

        // 別名與品類節點走 exposableStatusNames 而非 reviewStatuses：它們是實體不是關係，只擋 REJECTED
        Collection<String> exposableStatuses = ReviewScopes.exposableStatusNames();

        long totalElements = companyRepository.countCompanies(reviewStatuses, namePattern, country,
                query.getPublicCompany(), query.getItemId(), companyRole, reviewStatuses, exposableStatuses);
        List<Company> companies = companyRepository.findCompanies(reviewStatuses, namePattern, country,
                query.getPublicCompany(), query.getItemId(), companyRole, reviewStatuses, exposableStatuses,
                query.getSize(), offset);

        return PageResponse.of(toResponses(companies), query.getPage(), query.getSize(), totalElements);
    }

    /**
     * 把本頁公司組成回應，識別碼一次撈齊後再分派。
     *
     * <p>已駁回的識別碼在這裡就濾掉，與單筆查詢的 {@link #findVisibleIdentifiers} 同一套規則——
     * 同一家公司在列表與單筆回應裡的 {@code reference} 必須是同一個值，否則前端拿列表的識別去查單筆會 404。</p>
     */
    private List<CompanyResponse> toResponses(List<Company> companies) {
        if (companies.isEmpty()) {
            return List.of();
        }
        Set<Long> companyIds = companies.stream().map(Company::getId).collect(Collectors.toSet());
        Map<Long, List<CompanyIdentifier>> identifiersByCompanyId =
                companyIdentifierRepository.findByCompanyIdIn(companyIds).stream()
                        .filter(identifier -> ReviewScopes.isExposable(identifier.getReviewStatus()))
                        // 本頁公司已在同一交易內載入，關聯直接解析到它們本身而非代理
                        .collect(Collectors.groupingBy(identifier -> identifier.getCompany().getId()));

        return companies.stream()
                .map(company -> CompanyResponse.from(company,
                        identifiersByCompanyId.getOrDefault(company.getId(), List.of())))
                .toList();
    }

    /**
     * 空白字串一律視為「未指定」。
     *
     * <p>前端把所有查詢參數都帶上、值留空是常見行為（{@code ?country=}），照字面過濾會回 0 筆，
     * 使用者看到的是「一家都沒有」而不是「這個條件沒有生效」，兩者差很多。</p>
     */
    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    /**
     * 把名稱關鍵字轉成比對正規化名稱與別名的 LIKE 樣式；未指定關鍵字時不過濾。
     *
     * <p>比對正規化值而非顯示名稱，才能讓「ＴＳＭＣ」找得到別名為「tsmc」的台積電。
     * 關鍵字正規化後為空（全是標點或空白）時視為不過濾，而不是 400——搜尋條件與必填名稱不同，
     * 「剝完沒剩東西」的語意是「這個條件不構成過濾」。</p>
     */
    private String toNamePattern(String rawName) {
        String normalized = NameNormalizer.normalizeOrEmpty(rawName);
        return normalized.isEmpty() ? "%" : "%" + normalized + "%";
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
        Company company = resolveByIdentifier(reference, true)
                .orElseGet(() -> byNormalizedName(reference));
        if (!ReviewScopes.isExposable(company.getReviewStatus())) {
            throw new ServerException("查無此公司：" + reference, HttpStatus.NOT_FOUND);
        }
        return company;
    }

    /**
     * 取得單一公司的對外識別（design D4）：優先主要識別碼的限定形式，無識別碼才退回正規化名稱。
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
                        // 傳進來的公司都是同一交易內已載入的實體，關聯直接解析到它們本身而非代理
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
