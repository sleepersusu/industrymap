## 1. Repository 查詢

- [ ] 1.1 先寫失敗測試：只給 `companyRole` 不給 `itemId` 時，只回傳對任何零件具有該角色的公司；再改 SQL（design D1）
- [ ] 1.2 先寫失敗測試：**同一情境下 count 也必須一致**；再同步改 count 查詢（內容改了 count 沒改會讓清單有資料卻顯示 0 筆）
- [ ] 1.3 先寫失敗測試：只給角色時，同一公司對多個零件具有該角色仍只出現一次；再確認去重
- [ ] 1.4 先寫失敗測試：只給角色且未納入草稿時，草稿角色不得讓公司出現；再確認（design D3）
- [ ] 1.5 先寫失敗測試：`itemId` 與 `companyRole` 併用時，須由**同一筆**角色滿足兩者——
      對 A 零件有製造、對 B 零件有組裝的公司，查「A 零件＋組裝」不得命中；再確認單一 EXISTS 的寫法正確
- [ ] 1.6 先寫失敗測試：兩者皆不指定時不得排除任何公司（含完全無供應角色者）；再確認守衛的括號優先序（design D1 易錯處）
- [ ] 1.7 確認只給 `itemId` 的既有行為未變（回歸）

## 2. 查詢條件 payload

- [ ] 2.1 移除 `CompanyQuery` 的 `@AssertTrue`（`isCompanyRoleScopedToItem`）與其 javadoc（design D2）
- [ ] 2.2 改寫 `companyRole` 的 `@Schema` 說明：可獨立使用，併用零件時收斂為該零件的角色

## 3. Service 與 API 層

- [ ] 3.1 確認 `CompanyService.findCompanies` 無須改動（角色參數已直通 repository），若需改動則先寫測試
- [ ] 3.2 改寫 `CompanyControllerTest` 中「只給 companyRole 應回 400」的測試為驗證新語意（不可直接刪除）
- [ ] 3.3 更新 `GET /api/companies` 的 OpenAPI 說明，載明角色可獨立使用

## 4. 收尾

- [ ] 4.1 `./mvnw -DskipTests test-compile` 後跑相關測試確認全綠
- [ ] 4.2 `./mvnw clean verify` 跑全量，附實際輸出
- [ ] 4.3 以實際資料驗證：`?companyRole=ASSEMBLY`、`?companyRole=DESIGN&country=US`、
      以及既有的 `?itemId=&companyRole=` 組合未變，附實際回應
- [ ] 4.4 以 fresh-context diff review 審查（`/code-review`），只修正確性 findings
- [ ] 4.5 更新 `docs/CHANGELOG.md`
- [ ] 4.6 評估是否需要 `(company_role, review_status)` 索引——依實際查詢計畫決定，不預先加（design Risks）
