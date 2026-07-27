## ADDED Requirements

### Requirement: 內容變更後審核狀態退回草稿

當既有內容資料的實質欄位被修正時，系統 MUST 將該筆資料的審核狀態設為 `DRAFT`，
使其在重新審核前不對外可見。已驗證的資料 MUST NOT 被靜默改寫。

**「實質變更」的判準**：任一可修正欄位的值與現況不同。
若送入的值與現況**完全相同**，系統 MUST NOT 變更審核狀態——
否則重複送出相同內容會讓一筆已驗證資料無故從對外查詢消失。

#### Scenario: 修正已驗證的節點
- **WHEN** 修正一個 `VERIFIED` 節點的顯示名稱
- **THEN** 該節點的審核狀態 SHALL 變為 `DRAFT`，且 SHALL 不再出現於預設查詢結果

#### Scenario: 重新審核後恢復可見
- **WHEN** 修正後的節點再次被審核為 `VERIFIED`
- **THEN** 該節點 SHALL 重新出現於預設查詢結果

#### Scenario: 送出與現況相同的內容
- **WHEN** 以與現況完全相同的欄位值修正一個 `VERIFIED` 節點
- **THEN** 該節點的審核狀態 SHALL 維持 `VERIFIED`

#### Scenario: 修正草稿
- **WHEN** 修正一個 `DRAFT` 節點
- **THEN** 該節點的審核狀態 SHALL 維持 `DRAFT`

#### Scenario: 修正已駁回的資料
- **WHEN** 修正一個 `REJECTED` 節點
- **THEN** 該節點的審核狀態 SHALL 變為 `DRAFT`，取得重新審核的機會

#### Scenario: 修正失敗不影響審核狀態
- **WHEN** 修正因名稱衝突或循環而被拒絕
- **THEN** 該節點的審核狀態與所有欄位 MUST NOT 被變更
