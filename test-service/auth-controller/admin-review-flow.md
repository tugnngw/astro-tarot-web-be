# Luồng Admin Duyệt Reader Application

## Tổng Quan

Luồng này mô tả quy trình Admin review và approve/reject đơn đăng ký làm Reader của User.

**Controller:** `ReaderController.java` — endpoint `/api/v1/admin/readers/{applicationId}/review`
**Service:** `ReaderServiceImpl.java`
**Entities:** `ReaderApplication`, `ReaderProfile`, `User`

---

## Endpoint: `PATCH /api/v1/admin/readers/{applicationId}/review`

### Authorization

- Yêu cầu JWT hợp lệ trong header `Authorization: Bearer <token>`
- Role required: `ADMIN`
- Annotation: `@PreAuthorize("hasRole('ADMIN')")`

### Path Parameter

| Param           | Type | Description                        |
| :-------------- | :--- | :--------------------------------- |
| `applicationId` | UUID | ID của `ReaderApplication` cần review |

### Request DTO: `ReviewReaderRequest`

| Field            | Type   | Validation          | Description                        |
| :--------------- | :----- | :------------------ | :--------------------------------- |
| `action`         | String | `@NotBlank`         | `"APPROVED"` hoặc `"REJECTED"`     |
| `rejectionReason`| String | Optional            | Bắt buộc nếu action = REJECTED     |

### Response

```json
{
  "success": true,
  "message": "Application reviewed",
  "data": null,
  "timestamp": "2026-06-11T07:43:36"
}
```

---

## Luồng Xử Lý (ReaderServiceImpl.review)

### Input Validation & Authorization

1. Extract admin user từ `@AuthenticationPrincipal CustomUserDetails`
2. Load `ReaderApplication` từ DB theo `applicationId`
   - Nếu không tồn tại → throw `ApplicationNotFoundException`
3. Check `application.status == PENDING`
   - Nếu status != PENDING → throw `InvalidApplicationStatusException`
4. Load admin user từ DB theo `reviewerId`

### Action: APPROVED

**Logic:**

1. Update `ReaderApplication`:
   - `status = APPROVED`
   - `reviewedBy = admin`
   - `reviewedAt = Instant.now()`
2. Update `User` entity:
   - `role = READER` (thay đổi từ USER → READER)
3. Create `ReaderProfile`:
   - `user = application.user`
   - `bio = application.bio`
   - `yearsExperience = application.experience`
   - `specialties = application.specialties`
   - `verifiedAt = Instant.now()`
   - `available = true` (default)
   - `rating = 0.0` (default)
   - `totalReviews = 0` (default)
4. Save:
   - `readerApplicationRepository.save(application)`
   - `readerProfileRepository.save(profile)`
   - `userRepository.save(user)`

**Side Effects:**

- User có thể access các endpoint yêu cầu role `READER`
- Reader xuất hiện trên marketplace (nếu có filtering logic)
- User có thể set pricing, availability, nhận booking

### Action: REJECTED

**Logic:**

1. Update `ReaderApplication`:
   - `status = REJECTED`
   - `reviewedBy = admin`
   - `reviewedAt = Instant.now()`
   - `rejectionReason = request.rejectionReason`
2. User role **không thay đổi** (vẫn là USER)
3. **Không** tạo `ReaderProfile`
4. Save:
   - `readerApplicationRepository.save(application)`

**Side Effects:**

- User có thể reapply (nếu không có application PENDING nào khác)
- User nhận được feedback qua `rejectionReason`

---

## Test Scenarios

### ✅ Happy Path — APPROVED

| # | Scenario                     | Precondition                                      | Input                                         | Expected                                                                 |
| - | :--------------------------- | :------------------------------------------------ | :-------------------------------------------- | :----------------------------------------------------------------------- |
| 1 | Admin approve application    | Application `status=PENDING`, User `role=USER`    | `{ "action": "APPROVED" }`                    | 200 OK, `application.status=APPROVED`, `user.role=READER`, `ReaderProfile` created with `verifiedAt` set |

### ✅ Happy Path — REJECTED

| # | Scenario                     | Precondition                                      | Input                                                       | Expected                                                                 |
| - | :--------------------------- | :------------------------------------------------ | :---------------------------------------------------------- | :----------------------------------------------------------------------- |
| 2 | Admin reject application     | Application `status=PENDING`, User `role=USER`    | `{ "action": "REJECTED", "rejectionReason": "Insufficient experience" }` | 200 OK, `application.status=REJECTED`, `rejectionReason` saved, User role vẫn là USER, không tạo ReaderProfile |

### ❌ Authorization Errors (401/403)

| # | Scenario                     | Condition                                      | Expected                                      |
| - | :--------------------------- | :--------------------------------------------- | :-------------------------------------------- |
| 3 | Không có JWT token           | Header không có `Authorization`                | 401 Unauthorized                              |
| 4 | JWT token hết hạn            | Token expiry < now                             | 401 Unauthorized                              |
| 5 | JWT token không hợp lệ       | Token signature sai / malformed                | 401 Unauthorized                              |
| 6 | User không phải ADMIN        | JWT token hợp lệ nhưng role = USER/READER      | 403 Forbidden                                 |

### ❌ Validation Errors (400 Bad Request)

| # | Scenario                     | Input                          | Expected Error                |
| - | :--------------------------- | :----------------------------- | :---------------------------- |
| 7 | Action rỗng                  | `{ "action": "" }`             | `Action is required`          |
| 8 | Action không hợp lệ          | `{ "action": "PENDING" }`      | `Invalid action: PENDING`     |
| 9 | Action = null                | `{ "action": null }`           | `Action is required`          |

### ❌ Business Logic Errors

| #  | Scenario                          | Condition                                      | Expected                                                    |
| -- | :-------------------------------- | :--------------------------------------------- | :---------------------------------------------------------- |
| 10 | Application không tồn tại         | `applicationId` không match bất kỳ record nào | `ApplicationNotFoundException` → `Application not found`    |
| 11 | Application đã được review        | `application.status = APPROVED`                | `InvalidApplicationStatusException` → `Application status is not pending` |
| 12 | Application đã bị reject          | `application.status = REJECTED`                | `InvalidApplicationStatusException` → `Application status is not pending` |
| 13 | User đã là READER (edge case)     | `user.role = READER` trước khi approve         | Logic vẫn chạy, nhưng role không thay đổi (idempotent)     |

### 🔍 Edge Cases

| #  | Scenario                          | Condition                                      | Behavior                                                    |
| -- | :-------------------------------- | :--------------------------------------------- | :---------------------------------------------------------- |
| 14 | Approve application của user đã có ReaderProfile | User bằng cách nào đó đã có profile           | Code hiện tại sẽ tạo profile mới → có thể duplicate (cần kiểm tra unique constraint) |
| 15 | REJECTED không có rejectionReason | `{ "action": "REJECTED" }`                     | Validation hiện tại không enforce → có thể lưu null (nên thêm validation) |
| 16 | Admin tự approve application của chính mình | Admin cũng submit application                 | Logic cho phép (không có check conflict of interest)       |

---

## Database Changes

### Table: `reader_applications`

| Column            | Updated Value                          |
| :---------------- | :------------------------------------- |
| `status`          | `APPROVED` / `REJECTED`                |
| `reviewed_by`     | Admin `user_id`                        |
| `reviewed_at`     | `Instant.now()`                        |
| `rejection_reason`| String (nếu REJECTED)                  |

### Table: `users`

| Column | Updated Value (nếu APPROVED) |
| :----- | :--------------------------- |
| `role` | `READER`                     |

### Table: `reader_profiles` (nếu APPROVED)

| Column            | Value                                  |
| :---------------- | :------------------------------------- |
| `id`              | Auto-generated UUID                    |
| `user_id`         | `application.user.id`                  |
| `bio`             | `application.bio`                      |
| `years_experience`| `application.experience`               |
| `specialties`     | `application.specialties` (array)      |
| `verified_at`     | `Instant.now()`                        |
| `is_available`    | `true` (default)                       |
| `rating`          | `0.0` (default)                        |
| `total_reviews`   | `0` (default)                          |
| `created_at`      | Auto-generated                         |
| `updated_at`      | Auto-generated                         |

---

## Dependency Map

```
ReaderController.review()
    └── ReaderServiceImpl.review()
            ├── ReaderApplicationRepository.findById()
            ├── UserRepository.findById() (load admin)
            ├── UserRepository.save() (update user role if APPROVED)
            ├── ReaderProfileRepository.save() (create profile if APPROVED)
            └── ReaderApplicationRepository.save() (update application status)
```

---

## Recommended Validations (Chưa Implement)

1. **Enforce rejectionReason khi REJECTED:**
   ```java
   if ("REJECTED".equalsIgnoreCase(request.getAction()) && 
       (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
       throw new IllegalArgumentException("Rejection reason is required when rejecting");
   }
   ```

2. **Check duplicate ReaderProfile trước khi tạo:**
   ```java
   if (readerProfileRepository.existsByUserId(user.getId())) {
       throw new IllegalStateException("User already has a reader profile");
   }
   ```

3. **Log admin action cho audit trail:**
   ```java
   activityLogRepository.save(ActivityLog.builder()
       .action("REVIEW_READER_APPLICATION")
       .userId(admin.getId())
       .targetId(application.getId())
       .details(request.getAction())
       .build());
   ```

---

## Security Notes

- Endpoint yêu cầu JWT + role ADMIN → Spring Security filter chain handle
- `@PreAuthorize("hasRole('ADMIN')")` → nếu không pass → 403 Forbidden
- Rate limiting không áp dụng cho admin endpoints (hiện tại chỉ `/auth/**`)
- Admin có thể review bất kỳ application nào (không có ownership check)
