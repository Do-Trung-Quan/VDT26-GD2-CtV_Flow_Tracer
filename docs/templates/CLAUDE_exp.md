# CLAUDE.md

> Cẩm nang Onboarding & quy định hành vi cho **Claude Code Agent** khi làm việc local trên dự án này.
> Agent **phải đọc và tuân thủ tuyệt đối** file này trước khi sinh, sửa hoặc xóa bất kỳ dòng code nào.

---

## 1. Nguồn ngữ cảnh gốc (Source of Truth) & Dự án

### 1.1. Thư mục Ngữ cảnh Hệ thống (`.context/`)
Mọi logic code, thiết kế CSDL và luồng API được đặc tả chi tiết trong thư mục `.context/`. Agent **bắt buộc phải đọc và đối chiếu** dữ liệu trong các file này trước khi triển khai:
- **Cấu trúc thư mục mã nguồn:** `.context/PROJECT_STRUCTURE.md` (Chi tiết cấu trúc 6 module nghiệp vụ $\times$ 4 lớp DDD).
- **Cấu trúc bảng Cơ sở dữ liệu:** `.context/mp2_dbdiagram.dbml` (Chi tiết kiểu dữ liệu, khóa ngoại, ràng buộc NULL/NOT NULL của 8 bảng).
- **Đặc tả API Endpoints:** `.context/MP2_Danh_sach_API.docx` (Chi tiết cấu trúc REST và payload sự kiện WebSocket).
- **Bảng phân tích chức năng:** `.context/MP2_Bang_chuc_nang.docx` (Chi tiết input/output và quy định phân quyền của 38 chức năng).
- **Tài liệu Brief UI/UX:** `.context/Claude_Design_Brief_MP2.md` (Quy chuẩn thiết kế giao diện hệ thống).

### 1.2. Tổng quan Dự án
| Hạng mục | Giá trị |
| --- | --- |
| **Phân hệ** | MP2 — Intelligent Meeting Transcription System (vSM Platform) |
| **Vai trò** | Lớp xử lý âm thanh & chuyển đổi nội dung biên bản họp realtime |
| **Kiến trúc cốt lõi** | **Modular Monolith + Domain-Driven Design (DDD)** |
| **Stack** | NestJS · PostgreSQL · Redis · BullMQ · MinIO/S3 · Viettel AI (Speech2Text + Speaker Identify) |
| **Triển khai** | Docker (sau Nginx reverse proxy, SSL termination, cổng 443) |

**Phạm vi BỊ CẤM (không thuộc MP2 — tuyệt đối không tự thêm):**
- Cộng tác chỉnh sửa văn bản đồng thời, audio-text mapping cấp độ từ → **thuộc MP1**.
- Quản lý multi-tenant / quota → **thuộc MP3**.
- Widget nổi khi chạy nền, hay bất kỳ tính năng nào không có trong tài liệu đã chốt.

---

## 2. Lệnh điều hành dự án (Build / Test / Lint / Migration)

Agent gọi đúng các lệnh sau khi cần. **Không tự bịa lệnh.**

### 2.1. Cài đặt & chạy

```bash
npm install            # Cài đặt toàn bộ dependencies
npm run start:dev      # Chạy chế độ dev (watch mode, hot-reload)
npm run start:prod     # Chạy bản build production (dist/)
npm run build          # Biên dịch TypeScript -> dist/
npm run lint           # Kiểm tra & tự sửa lỗi style (ESLint --fix)
npm run format         # Định dạng code (Prettier)
```

### 2.2. Kiểm thử

```bash
npm run test           # Unit test (Jest)
npm run test:watch     # Unit test ở watch mode
npm run test:cov       # Unit test kèm coverage report
npm run test:e2e       # End-to-end test (test/ + jest-e2e config)
```

### 2.3. Migration database (TypeORM CLI)

> DataSource dùng cho CLI: `src/database/data-source.ts`. Các script dưới đây ánh xạ tới `typeorm-ts-node-commonjs -d src/database/data-source.ts`.

```bash
# Sinh migration tự động từ chênh lệch giữa entity và schema hiện tại
npm run migration:generate -- src/database/migrations/<TenMigration>

# Tạo file migration rỗng (viết SQL thủ công)
npm run migration:create -- src/database/migrations/<TenMigration>

# Áp dụng toàn bộ migration đang chờ
npm run migration:run

# Hoàn tác migration gần nhất
npm run migration:revert

# Xem trạng thái migration đã/chưa chạy
npm run migration:show
```

**Scripts tương ứng trong `package.json` (tham chiếu):**

```json
{
  "typeorm": "typeorm-ts-node-commonjs -d src/database/data-source.ts",
  "migration:generate": "npm run typeorm -- migration:generate",
  "migration:create":   "typeorm-ts-node-commonjs migration:create",
  "migration:run":      "npm run typeorm -- migration:run",
  "migration:revert":   "npm run typeorm -- migration:revert",
  "migration:show":     "npm run typeorm -- migration:show"
}
```

> ⚠️ Sau khi sửa bất kỳ entity nào, **bắt buộc** sinh migration mới — **KHÔNG** bật `synchronize: true` trên môi trường có dữ liệu.

---

## 3. Quy ước lập trình NGHIÊM NGẶT (Codestyle & Architecture)

### 3.1. Đặt tên & cấu trúc file

- **Tên file:** luôn `kebab-case` (ví dụ `create-live-meeting.handler.ts`).
- **Tên class:** `PascalCase`. **Biến/hàm:** `camelCase`. **Hằng/DI token:** `UPPER_SNAKE_CASE`.
- **Hậu tố bắt buộc — nói rõ vai trò:**

| Hậu tố | Lớp | Vai trò |
| --- | --- | --- |
| `.controller.ts` | presentation | REST endpoint |
| `.gateway.ts` | presentation | WebSocket gateway |
| `.handler.ts` | application | Xử lý 1 Use Case (command/query) |
| `.service.ts` | application | Orchestrator / service nghiệp vụ |
| `.dto.ts` | application | Input/Output đã validate |
| `.entity.ts` | domain | Thực thể nghiệp vụ (kiêm ORM, xem 3.3) |
| `.port.ts` | domain | Interface/Port (hợp đồng cho infrastructure) |
| `.repository.ts` | infrastructure | Hiện thực repository (TypeORM) |
| `.adapter.ts` | infrastructure | Hiện thực adapter ra ngoài (AI/S3/VAD/Redis...) |

### 3.2. Kiến trúc 4 tầng cách ly (trong MỖI module)

```
presentation  →  application  →  domain  ←  infrastructure
```

- Hướng phụ thuộc **một chiều** về `domain`. `infrastructure` phụ thuộc ngược vào `domain` qua **Port** (Dependency Inversion).
- **`presentation` và `infrastructure` TUYỆT ĐỐI KHÔNG chứa logic nghiệp vụ.** Mọi quy tắc nghiệp vụ nằm ở `application` (điều phối Use Case) và `domain` (invariant của entity).
- Mọi giao tiếp ra ngoài (DB, AI, S3, VAD, Redis, Mail) phải đi qua **Port** đặt tại `domain/ports/`. Application chỉ inject Port, **không bao giờ** import trực tiếp class adapter.
- CQRS cơ bản: tách `application/command/` (ghi) và `application/query/` (đọc).

### 3.3. Nguyên tắc thực dụng — GỘP Domain Entity & ORM Entity

- **Mỗi thực thể là MỘT class duy nhất** trong `domain/entities/`, dùng **trực tiếp decorator TypeORM** (`@Entity`, `@Column`, `@PrimaryGeneratedColumn('uuid')`, `@ManyToOne`, `@CreateDateColumn`...) để ánh xạ thẳng xuống bảng.
- **CẤM tạo file `*.orm-entity.ts` và `*.mapper.ts`.** Không có lớp trung gian chuyển đổi. Đây là đánh đổi có chủ đích để giảm boilerplate.
- Class entity vừa giữ **cột DB**, vừa giữ **method nghiệp vụ** (ví dụ `meeting.complete()`, `department.softDelete()`, `refreshToken.revoke()`).
- Repository hiện thực thao tác **trực tiếp trên `Repository<Entity>` của TypeORM**, không qua mapper.

### 3.4. An toàn dữ liệu — CHỈ Soft Delete

- **CẤM Hard Delete** (`DELETE` vật lý) đối với dữ liệu lịch sử của **User** và **Meeting** (và mọi dữ liệu cuộc họp liên quan).
- Cơ chế xóa/vô hiệu hóa bắt buộc:
  - `Meeting`, `Department`: xóa mềm qua trường **`deleted_at`** (NULL = còn sống).
  - `User`: vô hiệu hóa qua **`is_active = false`** (không xóa bản ghi).
  - `RefreshToken`: thu hồi qua **`is_revoked = true`**.
- Mọi truy vấn danh sách phải **lọc bản ghi còn sống** (`deleted_at IS NULL` / `is_active = true`) trừ khi nghiệp vụ yêu cầu khác (ví dụ chức năng khôi phục).

### 3.5. Ràng buộc nghiệp vụ đặc biệt (CỐT LÕI — không được vi phạm)

- **Xóa phòng ban có nhân sự:** CẤM xóa mềm một `Department` nếu `COUNT(User WHERE department_id = :id AND is_active = true) > 0`. Phải **ném `ConflictException`** (HTTP 409) — xử lý tại `delete-department.handler.ts`. Lý do: `User.department_id` là FK `NOT NULL`, không được tạo bản ghi mồ côi.
- **Phân quyền kế thừa:** `ADMIN` kế thừa 100% quyền `USER` (route cho USER thì ADMIN luôn pass).
- **Giới hạn dữ liệu theo phòng ban:** `USER` chỉ thấy/thao tác cuộc họp cùng `department_id` của mình (ép qua `DepartmentScopeGuard`); `ADMIN` xem toàn hệ thống.
- **Không Register tự do:** không tạo endpoint đăng ký công khai. Tài khoản do Admin khởi tạo, mật khẩu ngẫu nhiên gửi qua email.
- **Ghi âm Live:** ghi nối tiếp vào **file tạm local** trong khi họp; chỉ khi `end_session`/timeout mới upload file hoàn chỉnh lên MinIO/S3 → lấy URL lưu DB → **xóa file tạm**. S3 **không** stream append từng giây.

---

## 4. Bản đồ thành phần hệ thống (Maps)

### 4.1. Sáu module nghiệp vụ

| Module | Trách nhiệm | Thực thể sở hữu |
| --- | --- | --- |
| `auth` | Đăng nhập, refresh/rotation token, logout (revoke), quên & đặt lại mật khẩu (OTP) | `RefreshToken`, `PasswordResetOTP` |
| `users` | Hồ sơ cá nhân (xem/đổi mật khẩu/avatar) + quản trị nhân sự (Admin) | `User` |
| `departments` | CRUD phòng ban (Admin), xóa mềm có ràng buộc | `Department` |
| `meetings` | **TRỌNG TÂM:** cuộc họp, Live STT realtime (WS), Upload bóc băng nền, Reconnect, transcript, tóm tắt AI, full-text search, export PDF | `Meeting`, `TranscriptBlock`, `MeetingSummary` |
| `notifications` | Thông báo theo phòng ban (icon chuông), lắng nghe domain event | `Notification` |
| `dashboard` | Thống kê & báo cáo (Admin) — **chỉ đọc (CQRS Query)**, không sở hữu entity riêng | — |

### 4.2. Ánh xạ Ports (Domain) → Implementations (Infrastructure)

> Agent **bind đúng** theo bảng này trong `*.module.ts` qua DI token. **KHÔNG bind nhầm, KHÔNG inject thẳng class adapter vào application.**

| Port (Domain) | Implementation (Infrastructure) | Module |
| --- | --- | --- |
| `IUserRepository` | `UserRepository` | users |
| `IDepartmentRepository` | `DepartmentRepository` | departments |
| `IRefreshTokenRepository` | `RefreshTokenRepository` | auth |
| `IPasswordResetOtpRepository` | `PasswordResetOtpRepository` | auth |
| `IMeetingRepository` | `MeetingRepository` | meetings |
| `ITranscriptBlockRepository` | `TranscriptBlockRepository` | meetings |
| `IMeetingSummaryRepository` | `MeetingSummaryRepository` | meetings |
| `INotificationRepository` | `NotificationRepository` | notifications |
| `ISpeechToTextPort` | `ViettelSpeechToTextAdapter` | meetings |
| `ISpeakerEmbeddingPort` | `ViettelSpeakerEmbeddingAdapter` | meetings |
| `IVadPort` | `WebrtcVadAdapter` | meetings |
| `ILocalAudioStoragePort` | `LocalAudioStorageAdapter` | meetings |
| `ITranscriptBufferPort` | `RedisTranscriptBufferAdapter` | meetings |
| `ILiveSessionRegistryPort` | `RedisLiveSessionRegistryAdapter` | meetings |
| `IPdfExporterPort` | `PdfKitExporterAdapter` | meetings |
| `IObjectStoragePort` | `MinioObjectStorageAdapter` | shared/object-storage |
| `IMailerPort` | `SmtpMailerAdapter` | shared/mailer |
| `IEventPublisherPort` | `BullmqEventPublisherAdapter` | shared/event-bus |
| `IDashboardReadPort` | `DashboardReadRepository` | dashboard |

---

## 5. Checklist trước khi Agent kết thúc một tác vụ

- [ ] Đặt tên file/hậu tố đúng quy ước (mục 3.1).
- [ ] Code đặt đúng tầng; application chỉ inject **Port**, không inject adapter cụ thể (mục 3.2).
- [ ] Entity dùng decorator TypeORM trực tiếp; **không** sinh `orm-entity`/`mapper` (mục 3.3).
- [ ] Không có lệnh Hard Delete cho User/Meeting; dùng `deleted_at`/`is_active`/`is_revoked` (mục 3.4).
- [ ] Logic xóa Department chặn khi còn nhân sự; phân quyền & department-scope đúng (mục 3.5).
- [ ] Sửa entity → đã sinh migration mới (mục 2.3).
- [ ] DI bind đúng bảng Port→Implementation (mục 4.2).
- [ ] Đã chạy `npm run lint` và test liên quan trước khi báo hoàn thành.
