# PROJECT_STRUCTURE.md — Backend MP2 (NestJS)

> **Phân hệ MP2 — Hệ thống chuyển đổi nội dung biên bản họp realtime (vSM Platform)**
> Kiến trúc: **Modular Monolith + Domain-Driven Design (DDD)**, đóng gói trong Docker.
> Tài liệu này mô tả cấu trúc mã nguồn Backend, tách bạch theo **6 module nghiệp vụ** và **4 lớp DDD** trong từng module.

---

## 1. Quy ước & nguyên tắc tổ chức mã nguồn

Trước khi đi vào cây thư mục, đây là các quy ước được áp dụng xuyên suốt (bám đúng Guardrails đã chốt):

- **Hướng phụ thuộc (Dependency Rule):** `presentation → application → domain ← infrastructure`. Lớp `domain` nằm ở trung tâm; lớp `infrastructure` phụ thuộc ngược vào `domain` thông qua **Ports (interfaces)** — đúng nguyên lý Dependency Inversion. `presentation` và `infrastructure` **tuyệt đối không chứa logic nghiệp vụ**.
- **Domain Entity = ORM Entity (gộp một):** Để **tối ưu hóa thời gian triển khai**, dự án **kết hợp Domain Entity và ORM Entity làm một**. Mỗi thực thể trong `domain/entities/` vừa mang **quy tắc nghiệp vụ (method/invariant)**, vừa **sử dụng trực tiếp các Decorator của TypeORM** (`@Entity`, `@Column`, `@PrimaryGeneratedColumn('uuid')`, `@ManyToOne`...) để ánh xạ thẳng xuống bảng PostgreSQL. **Không tách `orm-entity` riêng và không cần lớp Mapper.** Đổi lại, lớp `domain` chấp nhận phụ thuộc vào TypeORM (đánh đổi có chủ đích để giảm boilerplate).
- **Ports vẫn đặt tại `domain/ports/`:** Mọi giao tiếp ra ngoài (DB, AI, S3, VAD, Redis, Mail...) vẫn đi qua interface `I*Port` / `I*Repository`. `infrastructure` cung cấp implementation và được **bind qua DI token** trong `*.module.ts`. Repository implementation **dùng trực tiếp `Repository<Entity>` của TypeORM** trên chính entity đã gắn decorator.
- **CQRS cơ bản:** `application/command/` cho luồng ghi (làm thay đổi trạng thái), `application/query/` cho luồng đọc. `application/dto/` chứa Input/Output DTO đã validate.
- **Naming:** file dùng `kebab-case`, class dùng `PascalCase`. Hậu tố rõ vai trò: `.controller.ts`, `.gateway.ts`, `.service.ts`, `.handler.ts`, `.dto.ts`, `.entity.ts`, `.port.ts`, `.repository.ts`, `.adapter.ts`.
- **Soft Delete & Audit:** không Hard Delete tài khoản/cuộc họp; xóa mềm qua `deleted_at`, vô hiệu hóa qua `is_active`/`is_revoked`.

**6 module nghiệp vụ:** `auth`, `users`, `departments`, `meetings`, `notifications`, `dashboard`.
**8 thực thể:** `User`, `Department`, `RefreshToken`, `PasswordResetOTP`, `Meeting`, `TranscriptBlock`, `MeetingSummary`, `Notification`.

---

## 2. Cây thư mục tổng quan (`src/`)

```text
backend/
├── src/
│   ├── main.ts                      # Điểm khởi động: tạo Nest app, bật global ValidationPipe, CORS, bind WS adapter, listen cổng nội bộ (sau Nginx)
│   ├── app.module.ts                # Root module: import 6 module nghiệp vụ + Config/Database/Queue/Shared module, đăng ký guard & filter toàn cục
│   │
│   ├── config/                      # Cấu hình & nạp biến môi trường (cross-cutting, không chứa nghiệp vụ)
│   ├── database/                    # Kết nối PostgreSQL, datasource, migrations
│   ├── queue/                       # Cấu hình BullMQ (Job Queue + EventBus) trên nền Redis
│   ├── common/                      # Guard, decorator, pipe, filter, interceptor dùng chung (Cross-cutting Concerns)
│   ├── shared/                      # Hạ tầng kỹ thuật dùng chung nhiều module: Object Storage, Mailer, Redis, EventBus port
│   │
│   └── modules/                     # 6 module nghiệp vụ — mỗi module tự chứa 4 lớp DDD
│       ├── auth/                    # Đăng nhập, refresh token, logout, quên/đặt lại mật khẩu (OTP)
│       ├── users/                   # Hồ sơ cá nhân + quản trị nhân sự (Admin)
│       ├── departments/             # Quản lý phòng ban (Admin)
│       ├── meetings/                # TRỌNG TÂM MP2: cuộc họp, Live STT realtime, Upload, Reconnect, Transcript, Summary, Search, Export
│       ├── notifications/           # Thông báo theo phòng ban (icon chuông)
│       └── dashboard/               # Thống kê & báo cáo (Admin, chỉ đọc — CQRS Query)
│
├── test/                            # E2E test
├── .env.example                     # Mẫu biến môi trường
├── Dockerfile                       # Đóng gói Backend container
├── docker-compose.yml               # Orchestrate: backend, postgres, redis, minio, nginx
├── nest-cli.json                    # Cấu hình Nest CLI
├── tsconfig.json                    # Cấu hình TypeScript + path alias (@modules, @common, @shared)
└── package.json                     # Khai báo dependencies & scripts
```

---

## 3. Hạ tầng nền tảng (config / database / queue / common / shared)

### 3.1. `config/` — Cấu hình ứng dụng

```text
src/config/
├── config.module.ts                 # Module global gói toàn bộ cấu hình, expose ConfigService cho mọi module
├── env.validation.ts                # Schema (Joi/zod) validate biến môi trường lúc khởi động; fail-fast nếu thiếu/sai
├── app.config.ts                    # Cấu hình app: PORT nội bộ, NODE_ENV, base URL, prefix /api
├── database.config.ts               # Cấu hình PostgreSQL: host, port, user, pass, dbname, ssl, pool size
├── redis.config.ts                  # Cấu hình Redis: host, port, password, dùng cho cache + BullMQ + transcript buffer
├── jwt.config.ts                    # Secret & thời hạn của access_token / refresh_token
├── storage.config.ts                # Cấu hình MinIO/S3: endpoint, bucket, access key, secret key, region
├── ai.config.ts                     # Endpoint & API key của Viettel AI (Speech2Text URL, SpeakerIdentify URL), timeout
└── transcription.config.ts          # Hằng số nghiệp vụ Live: TTL chờ resume, ngưỡng số phiên Live tối đa, giới hạn concurrency gọi AI, ngưỡng VAD silence
```

### 3.2. `database/` — Kết nối PostgreSQL

```text
src/database/
├── database.module.ts               # Khởi tạo TypeORM với database.config; tự động nạp các entity (đã gắn decorator) từ 6 module
├── data-source.ts                   # Định nghĩa DataSource độc lập cho TypeORM CLI (chạy migration ngoài runtime)
├── migrations/                      # Thư mục chứa toàn bộ migration đánh số theo thời gian
│   ├── 1700000000000-InitSchema.ts          # Tạo 8 bảng, khóa chính UUID, ràng buộc NOT NULL/UNIQUE, FK
│   ├── 1700000000001-AddSoftDeleteIndexes.ts # Index trên deleted_at (departments, meetings) phục vụ truy vấn bản ghi còn sống
│   └── 1700000000002-AddSearchIndexes.ts     # Index hỗ trợ full-text search trên transcript_blocks.text & meetings.title
└── seeds/
    └── seed-admin.ts                # Seed tài khoản Admin gốc đầu tiên (không có luồng Register tự do cho hệ thống)
```

### 3.3. `queue/` — Job Queue & EventBus (BullMQ)

```text
src/queue/
├── queue.module.ts                  # Đăng ký BullMQ root connection (Redis), khai báo các queue dùng chung
├── queue.constants.ts               # Tên queue & job: 'transcription-batch', 'summary-generation', 'domain-events'
└── README.md                        # Ghi chú: vì sao BullMQ vừa làm Job Queue (xử lý nền) vừa làm EventBus (domain event) trên Redis
```

### 3.4. `common/` — Cross-cutting Concerns (Presentation-level)

```text
src/common/
├── guards/
│   ├── jwt-auth.guard.ts            # Guard toàn cục: xác thực access_token (JWT), gắn user vào request; bỏ qua route gắn @Public
│   ├── roles.guard.ts               # Kiểm tra role theo @Roles(); CỐT LÕI: Admin kế thừa 100% quyền User (USER pass thì ADMIN cũng pass)
│   └── department-scope.guard.ts    # CỐT LÕI: ép giới hạn dữ liệu theo department_id của User; Admin được bỏ qua (xem toàn hệ thống)
├── decorators/
│   ├── current-user.decorator.ts    # @CurrentUser() — trích user (id, role, department_id) đã giải mã từ JWT
│   ├── roles.decorator.ts           # @Roles('ADMIN') — gắn metadata vai trò yêu cầu cho route
│   └── public.decorator.ts          # @Public() — đánh dấu route bỏ qua JwtAuthGuard (vd: /auth/login)
├── pipes/
│   └── parse-uuid-or-400.pipe.ts    # Pipe validate tham số :id là UUID hợp lệ, ném 400 nếu sai
├── filters/
│   └── all-exceptions.filter.ts     # Filter toàn cục: chuẩn hóa mọi lỗi về cấu trúc JSON thống nhất { statusCode, message, error }
├── interceptors/
│   ├── transform-response.interceptor.ts # Bọc response thành cấu trúc chuẩn { data, meta } cho REST
│   └── logging.interceptor.ts       # Log request/response (method, path, latency) phục vụ giám sát
├── dto/
│   ├── pagination-query.dto.ts      # DTO input phân trang dùng chung: page, limit (validate min/max)
│   └── paginated-result.dto.ts      # DTO output phân trang dùng chung: items, total, page, limit, totalPages
└── domain/
    ├── domain-event.base.ts         # Lớp cơ sở cho mọi Domain Event (eventName, occurredAt, payload)
    └── aggregate-root.base.ts       # Lớp cơ sở Aggregate Root: gom domain event chờ publish sau khi lưu thành công
```

### 3.5. `shared/` — Hạ tầng kỹ thuật dùng chung nhiều module

> Đây là các adapter kỹ thuật được **nhiều module** dùng (tránh trùng lặp). Mỗi adapter vẫn được expose qua **Port**, module nghiệp vụ chỉ phụ thuộc Port.

```text
src/shared/
├── event-bus/
│   ├── event-bus.module.ts          # Module phát/nhận domain event qua BullMQ queue 'domain-events'
│   ├── ports/
│   │   └── event-publisher.port.ts  # IEventPublisherPort: publish(event) — hợp đồng để Application publish domain event
│   ├── publishers/
│   │   └── bullmq-event-publisher.adapter.ts # Triển khai IEventPublisherPort: đẩy event vào queue Redis, đảm bảo không mất khi restart
│   └── event-bus.tokens.ts          # DI token: EVENT_PUBLISHER_PORT
├── object-storage/
│   ├── object-storage.module.ts     # Module bao bọc client MinIO/S3 dùng chung (audio cuộc họp + avatar người dùng)
│   ├── ports/
│   │   └── object-storage.port.ts   # IObjectStoragePort: upload(buffer,key) → url, getSignedUrl(key), remove(key)
│   ├── adapters/
│   │   └── minio-object-storage.adapter.ts # Triển khai IObjectStoragePort bằng SDK MinIO/S3; trả về URL bền vững lưu vào DB
│   └── object-storage.tokens.ts     # DI token: OBJECT_STORAGE_PORT
├── mailer/
│   ├── mailer.module.ts             # Module gửi email (cấp mật khẩu ngẫu nhiên khi Admin tạo tài khoản; gửi mã OTP)
│   ├── ports/
│   │   └── mailer.port.ts           # IMailerPort: sendPasswordEmail(to,password), sendOtpEmail(to,otp)
│   ├── adapters/
│   │   └── smtp-mailer.adapter.ts   # Triển khai IMailerPort qua SMTP (nodemailer)
│   └── mailer.tokens.ts             # DI token: MAILER_PORT
└── redis/
    ├── redis.module.ts              # Cung cấp Redis client (ioredis) dùng chung cho cache/buffer/đếm phiên
    └── redis.provider.ts            # Factory tạo & quản lý vòng đời kết nối Redis
```

---

## 4. Module `auth/` — Xác thực & vòng đời phiên đăng nhập

**Phạm vi:** Đăng nhập, làm mới token, đăng xuất (revoke), quên mật khẩu (gửi OTP), đặt lại mật khẩu bằng OTP.
**Thực thể quản lý:** `RefreshToken`, `PasswordResetOTP` (đọc `User` để xác thực).
**Lưu ý nghiệp vụ:** **Không có luồng Register tự do** — tài khoản do Admin tạo (thuộc module `users`).

```text
src/modules/auth/
├── auth.module.ts                   # Khai báo module auth; TypeOrmModule.forFeature([RefreshToken, PasswordResetOTP]); bind các Port với impl
│
├── presentation/
│   └── auth.controller.ts           # REST controller (@Public ở các route công khai):
│                                    #   POST /auth/login            → đăng nhập, cấp access+refresh token
│                                    #   POST /auth/refresh          → đổi refresh_token lấy access_token mới
│                                    #   POST /auth/logout           → revoke refresh_token hiện tại (is_revoked=true)
│                                    #   POST /auth/forgot-password  → sinh & gửi OTP qua email
│                                    #   POST /auth/reset-password   → xác thực OTP còn hạn & đặt mật khẩu mới
│
├── application/
│   ├── command/
│   │   ├── login.handler.ts         # Xác thực email/password (so bcrypt), phát hành access_token + lưu refresh_token đã băm
│   │   ├── refresh-token.handler.ts # Kiểm tra refresh_token hợp lệ & chưa revoke/hết hạn, xoay token mới (rotation)
│   │   ├── logout.handler.ts        # Đặt is_revoked=true cho refresh_token đang dùng
│   │   ├── forgot-password.handler.ts # Tạo PasswordResetOTP (mã + expires_at), gọi IMailerPort gửi OTP
│   │   └── reset-password.handler.ts  # Verify OTP (chưa dùng, còn hạn), cập nhật password_hash, đánh dấu is_used=true
│   ├── services/
│   │   ├── token.service.ts         # Sinh/ký/giải mã JWT access token; tính payload phân quyền (id, role, department_id)
│   │   └── password-hash.service.ts # Băm & so khớp mật khẩu (bcrypt/argon2) — dùng chung cho auth & users
│   └── dto/
│       ├── login.dto.ts             # Input: email, password (validate định dạng email, độ dài)
│       ├── refresh-token.dto.ts     # Input: refresh_token
│       ├── forgot-password.dto.ts   # Input: email
│       ├── reset-password.dto.ts    # Input: email, otp_code, new_password (validate độ mạnh mật khẩu)
│       └── auth-response.dto.ts     # Output: access_token, refresh_token, user{ id, full_name, role, department_id }
│
├── domain/
│   ├── entities/
│   │   ├── refresh-token.entity.ts  # @Entity('refresh_tokens'): cột id, user_id, token_hash, is_revoked, expires_at, created_at + method revoke()/isExpired()
│   │   └── password-reset-otp.entity.ts # @Entity('password_reset_otps'): cột id, user_id, otp_code, is_used, expires_at, created_at + method markUsed()/isValid(now)
│   └── ports/
│       ├── refresh-token.repository.port.ts   # IRefreshTokenRepository: save, findByHash, revokeAllByUser
│       └── password-reset-otp.repository.port.ts # IPasswordResetOtpRepository: save, findActiveByUser, markUsed
│
└── infrastructure/
    └── repositories/
        ├── refresh-token.repository.ts        # Triển khai IRefreshTokenRepository qua Repository<RefreshToken> của TypeORM
        └── password-reset-otp.repository.ts   # Triển khai IPasswordResetOtpRepository qua Repository<PasswordResetOTP> của TypeORM
```

---

## 5. Module `users/` — Hồ sơ cá nhân & quản trị nhân sự

**Phạm vi:** Tự phục vụ (xem hồ sơ, đổi mật khẩu, đổi avatar) + quản trị nhân sự bởi Admin (tạo tài khoản, sửa, vô hiệu hóa/kích hoạt).
**Thực thể quản lý:** `User`.
**Lưu ý:** `email`/`employee_id` không cho sửa sau khi tạo; vô hiệu hóa qua `is_active` (không Hard Delete).

```text
src/modules/users/
├── users.module.ts                  # TypeOrmModule.forFeature([User]); bind IUserRepository, IObjectStoragePort (avatar), IMailerPort, PasswordHashService
│
├── presentation/
│   ├── me.controller.ts             # REST self-service (yêu cầu JWT):
│   │                                #   GET   /users/me           → hồ sơ tài khoản đang đăng nhập
│   │                                #   PATCH /users/me/password  → đổi mật khẩu (xác thực old_password)
│   │                                #   PATCH /users/me/avatar    → upload avatar lên Object Storage, cập nhật avatar_url
│   └── admin-users.controller.ts    # REST quản trị (@Roles('ADMIN')):
│                                    #   GET   /admin/users        → danh sách nhân sự (phân trang, filter phòng ban/trạng thái, keyword)
│                                    #   POST  /admin/users        → tạo tài khoản mới, sinh mật khẩu ngẫu nhiên gửi email
│                                    #   PATCH /admin/users/:id    → sửa role, department_id (không đụng avatar/password)
│                                    #   PATCH /admin/users/:id/status → vô hiệu hóa/kích hoạt (is_active)
│
├── application/
│   ├── command/
│   │   ├── create-user.handler.ts   # Tạo User: kiểm tra trùng email/employee_id, sinh mật khẩu ngẫu nhiên, băm, gửi mail
│   │   ├── update-user.handler.ts   # Cập nhật role/department_id; chặn sửa email/employee_id
│   │   ├── set-user-status.handler.ts # Bật/tắt is_active; revoke toàn bộ refresh_token khi vô hiệu hóa
│   │   ├── change-password.handler.ts # Self-service: xác thực old_password rồi cập nhật password_hash
│   │   └── update-avatar.handler.ts # Upload file ảnh qua IObjectStoragePort, ghi avatar_url vào User
│   ├── query/
│   │   ├── get-my-profile.handler.ts # Lấy hồ sơ theo user_id từ token
│   │   └── list-users.handler.ts    # Truy vấn danh sách User (phân trang + filter + search)
│   └── dto/
│       ├── create-user.dto.ts       # Input: email, full_name, employee_id, department_id, role
│       ├── update-user.dto.ts       # Input: role?, department_id?
│       ├── set-user-status.dto.ts   # Input: is_active
│       ├── change-password.dto.ts   # Input: old_password, new_password
│       ├── list-users-query.dto.ts  # Input: page, limit, department_id?, is_active?, keyword?
│       ├── user-profile.dto.ts      # Output: id, full_name, email, employee_id, avatar_url, role, department_id
│       └── user-list-item.dto.ts    # Output rút gọn cho danh sách quản trị
│
├── domain/
│   ├── entities/
│   │   └── user.entity.ts           # @Entity('users'): đầy đủ cột theo thiết kế DB, @ManyToOne→Department (department_id NOT NULL); method deactivate()/activate(), canSeeMeetingOf(deptId), isAdmin()
│   └── ports/
│       └── user.repository.port.ts  # IUserRepository: save, findById, findByEmail, existsByEmployeeId, listPaginated, countActive
│
└── infrastructure/
    └── repositories/
        └── user.repository.ts       # Triển khai IUserRepository qua Repository<User> của TypeORM (lọc is_active, phân trang)
```

---

## 6. Module `departments/` — Quản lý phòng ban

**Phạm vi:** CRUD phòng ban (Admin). Xóa mềm có **ràng buộc đặc biệt**.
**Thực thể quản lý:** `Department`.
**Lưu ý CỐT LÕI:** **Xóa mềm phòng ban chỉ thành công khi không còn User nào thuộc phòng ban đó**; nếu còn nhân sự → trả về lỗi (vì `User.department_id` là FK NOT NULL, không được tạo bản ghi mồ côi).

```text
src/modules/departments/
├── departments.module.ts            # TypeOrmModule.forFeature([Department]); bind IDepartmentRepository; inject IUserRepository (read-only) để kiểm tra còn nhân sự
│
├── presentation/
│   └── admin-departments.controller.ts # REST quản trị (@Roles('ADMIN')):
│                                    #   GET    /admin/departments          → danh sách phòng ban (phân trang)
│                                    #   POST   /admin/departments          → tạo phòng ban mới (kiểm tra trùng tên trong nhóm còn hoạt động)
│                                    #   PATCH  /admin/departments/:id       → sửa tên (kiểm tra không trùng tên phòng ban đang hoạt động)
│                                    #   DELETE /admin/departments/:id       → XÓA MỀM (chặn nếu còn User), set deleted_at
│                                    #   POST   /admin/departments/:id/restore → khôi phục phòng ban đã xóa mềm (nếu còn hạn)
│
├── application/
│   ├── command/
│   │   ├── create-department.handler.ts  # Tạo Department; kiểm tra UNIQUE name trong số phòng ban deleted_at IS NULL
│   │   ├── update-department.handler.ts  # Đổi tên; kiểm tra không trùng tên phòng ban đang hoạt động
│   │   ├── delete-department.handler.ts  # CỐT LÕI: đếm User thuộc phòng ban; còn nhân sự → ném ConflictException; rỗng → set deleted_at
│   │   └── restore-department.handler.ts # Khôi phục: set deleted_at = NULL nếu còn trong thời hạn cho phép
│   ├── query/
│   │   └── list-departments.handler.ts   # Truy vấn danh sách phòng ban còn hoạt động (phân trang)
│   └── dto/
│       ├── create-department.dto.ts      # Input: name
│       ├── update-department.dto.ts      # Input: name
│       ├── list-departments-query.dto.ts # Input: page, limit
│       └── department.dto.ts             # Output: id, name, address?, description?, created_at
│
├── domain/
│   ├── entities/
│   │   └── department.entity.ts     # @Entity('departments'): cột id, name(UNIQUE active), address, description, deleted_at, timestamps; method softDelete(), restore(), isActive()
│   └── ports/
│       └── department.repository.port.ts # IDepartmentRepository: save, findById, existsActiveByName, listPaginated, countUsersIn(departmentId)
│
└── infrastructure/
    └── repositories/
        └── department.repository.ts # Triển khai IDepartmentRepository qua Repository<Department>; countUsersIn dùng để chặn xóa khi còn nhân sự
```

---

## 7. Module `meetings/` — TRỌNG TÂM MP2

**Phạm vi:** Toàn bộ vòng đời cuộc họp + nghiệp vụ lõi MP2: tạo Live, ghi âm realtime (WS), Upload + bóc băng nền, **Reconnect bù dữ liệu**, transcript, tóm tắt AI, tìm kiếm toàn văn, xuất PDF, quản trị (Admin: sửa/khôi phục/khóa).
**Thực thể quản lý:** `Meeting`, `TranscriptBlock`, `MeetingSummary`.
**Lưu ý CỐT LÕI:**
- Ghi âm Live → **ghi nối tiếp vào file tạm local**; chỉ khi kết thúc/timeout mới upload file hoàn chỉnh lên MinIO/S3, lấy URL lưu DB, rồi **xóa file tạm** (S3 không stream append).
- Reconnect: bù `missed_blocks` từ Redis theo `last_received_sequence` + stream lại audio buffer cục bộ.
- Giới hạn **concurrency gọi AI dùng chung toàn hệ thống** và **ngưỡng số phiên Live đồng thời**.

```text
src/modules/meetings/
├── meetings.module.ts               # TypeOrmModule.forFeature([Meeting, TranscriptBlock, MeetingSummary]); bind toàn bộ Port:
│                                    #   IMeetingRepository, ITranscriptBlockRepository, IMeetingSummaryRepository,
│                                    #   ISpeechToTextPort, ISpeakerIdentifyPort, IVadPort, ILocalAudioStoragePort, ITranscriptBufferPort,
│                                    #   ILiveSessionRegistryPort, IPdfExporterPort, IObjectStoragePort, IEventPublisherPort; đăng ký BullMQ processor
│
├── presentation/
│   ├── meetings.controller.ts       # REST tra cứu theo phòng ban (JWT + DepartmentScopeGuard):
│   │                                #   GET    /meetings              → danh sách (ưu tiên LIVE→PROCESSING→COMPLETED, rồi created_at desc)
│   │                                #   GET    /meetings/search       → tìm theo title trong phạm vi phòng ban
│   │                                #   GET    /meetings/:id          → chi tiết Meeting + audio_url (hỗ trợ HTTP Range cho player)
│   │                                #   GET    /meetings/:id/transcript → danh sách TranscriptBlock (đồng bộ theo audio)
│   │                                #   DELETE /meetings/:id          → xóa mềm (User chỉ xóa cuộc họp mình host; Admin xóa mọi cuộc họp)
│   ├── live-meetings.controller.ts  # REST khởi tạo nguồn cuộc họp (Group F):
│   │                                #   POST /meetings/live           → tạo Meeting type=LIVE, status=LIVE (department_id ép theo User nếu không phải Admin)
│   │                                #   POST /meetings/upload         → tạo Meeting type=UPLOAD, status=PROCESSING, đẩy job bóc băng nền
│   ├── admin-meetings.controller.ts # REST quản trị (@Roles('ADMIN'), Group C):
│   │                                #   GET   /admin/meetings         → danh sách toàn hệ thống (không giới hạn phòng ban)
│   │                                #   PATCH /admin/meetings/:id      → sửa title/description/department_id (điều chuyển), bắn thông báo phòng ban cũ/mới
│   │                                #   POST  /admin/meetings/:id/restore → khôi phục cuộc họp đã xóa mềm
│   │                                #   PATCH /admin/meetings/:id/lock → khóa/mở khóa biên bản (is_locked, bảo vệ tính pháp lý)
│   ├── summary.controller.ts        # REST tóm tắt AI (Group G):
│   │                                #   POST /meetings/:id/summary    → trigger sinh tóm tắt (tạo MeetingSummary status=PROCESSING, trả 202)
│   │                                #   GET  /meetings/:id/summary    → lấy tóm tắt (trả 202 nếu PROCESSING, 200 + summary_text nếu COMPLETED)
│   ├── search.controller.ts         # REST tìm kiếm toàn văn (Group G):
│   │                                #   GET /meetings/full-text-search → tra cứu theo nội dung transcript (không chỉ title), filter phòng ban/ngày
│   ├── export.controller.ts         # REST xuất tài liệu (Group G):
│   │                                #   GET /meetings/:id/export/pdf  → sinh & trả file PDF biên bản (binary/đường dẫn tải)
│   └── transcription.gateway.ts     # WebSocket Gateway (Group F realtime) — CỐT LÕI MP2, xử lý các event:
│                                    #   'open_session'      → xác thực JWT, kiểm ngưỡng số phiên Live, khởi tạo buffer Redis + VAD instance
│                                    #   'audio_chunk'       → nhận byte audio thô, ghi nối tiếp file tạm, đẩy vào VAD
│                                    #   emit 'transcript_update' → trả block hoàn chỉnh (text, speaker, start/end_time) mỗi khi VAD cắt xong
│                                    #   'edit_speaker'      → đổi nhãn người nói lúc đang live, áp dụng cho phát biểu tiếp theo (Group G)
│                                    #   'end_session'       → kích hoạt Finalize (đóng file, upload S3, lưu transcript, COMPLETED)
│                                    #   'resume'            → reconnect: bù missed_blocks theo last_received_sequence (Scenario 3)
│                                    #   handleDisconnect()  → báo Application đặt TTL chờ resume trên Redis
│
├── application/
│   ├── command/
│   │   ├── create-live-meeting.handler.ts   # Tạo Meeting LIVE; ép department_id theo Host nếu role=USER
│   │   ├── upload-audio-meeting.handler.ts  # Lưu tạm file upload, tạo Meeting PROCESSING, publish AudioUploadedEvent để worker xử lý
│   │   ├── soft-delete-meeting.handler.ts   # Kiểm tra quyền (host hoặc Admin) rồi set deleted_at
│   │   ├── restore-meeting.handler.ts       # Admin khôi phục cuộc họp đã xóa mềm
│   │   ├── update-meeting-info.handler.ts   # Admin sửa thông tin/điều chuyển phòng ban; phát MeetingInfoUpdatedEvent
│   │   ├── lock-meeting.handler.ts          # Admin đặt is_locked true/false
│   │   ├── edit-speaker-label.handler.ts    # Đổi speaker_label cho block từ thời điểm sửa trở đi (lúc live)
│   │   └── generate-summary.handler.ts      # Tạo MeetingSummary PROCESSING, đẩy job 'summary-generation' gọi AI tóm tắt
│   ├── query/
│   │   ├── list-meetings.handler.ts         # Danh sách theo phòng ban (sắp xếp trạng thái + created_at)
│   │   ├── search-meetings.handler.ts       # Tìm theo title trong phạm vi phòng ban
│   │   ├── list-all-meetings.handler.ts     # Admin: danh sách toàn hệ thống
│   │   ├── get-meeting-detail.handler.ts    # Chi tiết Meeting + audio_url
│   │   ├── get-transcript.handler.ts        # Lấy danh sách TranscriptBlock theo sequence_number
│   │   ├── get-summary.handler.ts           # Lấy MeetingSummary (phục vụ cơ chế 202/200)
│   │   └── full-text-search.handler.ts      # Tìm kiếm toàn văn trên transcript_blocks.text
│   ├── streaming/                           # Orchestrator cho luồng realtime (đặt trong application — là Use Case, không phải hạ tầng)
│   │   ├── transcription.service.ts         # Điều phối 1 phiên Live: nhận audio → convert WAV → VAD → batch ASR+Embedding → online clustering → tạo block → emit
│   │   ├── speaker-diarization.service.ts   # CỐT LÕI: Online clustering (live) và offline clustering (upload) bằng cosine similarity; quản lý speaker_registry
│   │   ├── audio-converter.ts               # Tiện ích chuyển đổi audio: raw PCM / WebM → WAV 16kHz mono (ffmpeg-static); dùng chung live + batch
│   │   ├── live-session.service.ts          # Quản lý vòng đời phiên: open (kiểm ngưỡng), tick, disconnect (đặt TTL), resume (bù missed_blocks)
│   │   ├── reconnect.service.ts             # Xử lý resume: đối chiếu last_received_sequence với buffer Redis, dựng lại/kiểm tra VAD instance
│   │   └── finalize-session.service.ts      # CỐT LÕI: đóng file tạm → upload Object Storage → lấy URL ghi DB → bulkSave transcript → xóa file tạm → COMPLETED
│   ├── workers/                             # Xử lý nền qua BullMQ (chạy ngoài request HTTP)
│   │   ├── batch-transcription.processor.ts # Worker luồng Upload: tải file, VAD phân đoạn, gọi STT+SpeakerIdentify (theo concurrency), lưu transcript, COMPLETED
│   │   └── summary-generation.processor.ts  # Worker sinh tóm tắt: gọi AI tóm tắt transcript, cập nhật MeetingSummary=COMPLETED
│   ├── listeners/
│   │   └── live-session-timeout.listener.ts # Lắng nghe TTL chờ resume hết hạn → tự động finalize (Scenario 3, nhánh timeout)
│   └── dto/
│       ├── create-live-meeting.dto.ts       # Input: title, description?, department_id? (Admin)
│       ├── upload-audio-meeting.dto.ts      # Input: title, description?, audio_file (multipart), department_id? (Admin)
│       ├── update-meeting-info.dto.ts       # Input: title?, description?, department_id?
│       ├── lock-meeting.dto.ts              # Input: is_locked
│       ├── list-meetings-query.dto.ts       # Input: page, limit, status?, from_date?, to_date?
│       ├── admin-list-meetings-query.dto.ts # Input: + department_id?
│       ├── full-text-search-query.dto.ts    # Input: keyword, department_id?, from_date?, to_date?, page, limit
│       ├── edit-speaker.dto.ts              # Input: meeting_id, old_speaker_label, new_speaker_label
│       ├── ws-open-session.dto.ts           # Input WS: meeting_id, JWT token
│       ├── ws-audio-chunk.dto.ts            # Input WS: meeting_id, audio binary
│       ├── ws-resume.dto.ts                 # Input WS: meeting_id, JWT token, last_received_sequence
│       ├── transcript-block.dto.ts          # Output: id, sequence_number, text, speaker_label, start_time, end_time
│       ├── meeting-detail.dto.ts            # Output: thông tin Meeting + audio_url
│       └── summary.dto.ts                   # Output: status, summary_text?
│
├── domain/
│   ├── entities/
│   │   ├── meeting.entity.ts         # @Entity('meetings') + Aggregate Root: đầy đủ cột (FK host_id/department_id, deleted_at); method startLive(), markProcessing(), complete(audioUrl,duration), lock()/unlock(), softDelete(); gom domain event
│   │   ├── transcript-block.entity.ts # @Entity('transcript_blocks'): cột meeting_id, sequence_number, text, speaker_label, start/end_time; ràng buộc start_time<end_time; method renameSpeaker()
│   │   └── meeting-summary.entity.ts # @Entity('meeting_summaries'): meeting_id UNIQUE (1-1), summary_text, status; method markCompleted(text)
│   ├── events/
│   │   ├── meeting-created.event.ts          # Domain event: cuộc họp mới được tạo trong phòng ban → notifications lắng nghe
│   │   ├── meeting-status-changed.event.ts   # Domain event: đổi trạng thái (→ COMPLETED) → notifications lắng nghe
│   │   ├── meeting-info-updated.event.ts     # Domain event: Admin sửa thông tin → notifications (phòng ban cũ & mới)
│   │   └── live-session-ended.event.ts       # Domain event: phiên live kết thúc (chủ động/timeout) → kích hoạt finalize
│   └── ports/
│       ├── meeting.repository.port.ts        # IMeetingRepository: save, findById, listByDepartment, listAll, searchByTitle, countActive
│       ├── transcript-block.repository.port.ts # ITranscriptBlockRepository: bulkSave, findByMeeting, fullTextSearch, updateSpeakerLabelFrom(seq)
│       ├── meeting-summary.repository.port.ts # IMeetingSummaryRepository: save, findByMeeting (UNIQUE meeting_id)
│       ├── speech-to-text.port.ts            # ISpeechToTextPort: batchTranscribe(segments: Buffer[]) → string[] — Viettel ASR Sherpa (batch, WAV 16kHz mono)
│       ├── speaker-embedding.port.ts         # ISpeakerEmbeddingPort: batchGetEmbeddings(segments: Buffer[]) → (number[]|null)[] — Viettel Embedding 512-dim vector
│       ├── vad.port.ts                       # IVadPort: feed(audio) → segment[] hoàn chỉnh kèm start/end_time; quản lý VAD instance theo phiên (WebRTC VAD)
│       ├── local-audio-storage.port.ts       # ILocalAudioStoragePort: append(meetingId,chunk), close(meetingId)→path, remove(path)
│       ├── transcript-buffer.port.ts         # ITranscriptBufferPort (Redis): push(block), getAfter(seq), setResumeTtl, clearResumeTtl, drain
│       ├── live-session-registry.port.ts     # ILiveSessionRegistryPort (Redis): countLive, increment/decrement, acquireAiSlot/release (concurrency)
│       └── pdf-exporter.port.ts              # IPdfExporterPort: export(meeting, transcript) → PDF buffer
│
└── infrastructure/
    ├── repositories/
    │   ├── meeting.repository.ts             # Triển khai IMeetingRepository qua Repository<Meeting> (lọc deleted_at, sắp xếp trạng thái, phân trang)
    │   ├── transcript-block.repository.ts    # Triển khai ITranscriptBlockRepository qua Repository<TranscriptBlock> (bulkSave, full-text search bằng index PostgreSQL)
    │   └── meeting-summary.repository.ts     # Triển khai IMeetingSummaryRepository qua Repository<MeetingSummary>
    └── adapters/
        ├── viettel-speech-to-text.adapter.ts  # Triển khai ISpeechToTextPort: POST batch WAV → /api/transcribe/batch/sherpa, parse [{transcript}], retry/timeout
        ├── viettel-speaker-embedding.adapter.ts # Triển khai ISpeakerEmbeddingPort: POST batch WAV → /api/diarization/embedding, parse [{embedding:float[512]|null}]
        ├── webrtc-vad.adapter.ts             # Triển khai IVadPort: node-vad (WebRTC algo, không cần ONNX), cắt PCM 16kHz theo silence >600ms
        ├── local-audio-storage.adapter.ts    # Triển khai ILocalAudioStoragePort: ghi nối tiếp file tạm trên đĩa server, đóng & xóa khi finalize
        ├── redis-transcript-buffer.adapter.ts # Triển khai ITranscriptBufferPort: lưu đệm block + đặt/hủy TTL resume trên Redis
        ├── redis-live-session-registry.adapter.ts # Triển khai ILiveSessionRegistryPort: bộ đếm phiên Live & semaphore concurrency AI (Redis)
        └── pdfkit-exporter.adapter.ts        # Triển khai IPdfExporterPort: dựng PDF biên bản (pdfkit/puppeteer)
```

---

## 8. Module `notifications/` — Thông báo theo phòng ban

**Phạm vi:** Sinh thông báo khi cuộc họp trong phòng ban được tạo / đổi trạng thái / bị Admin sửa thông tin; người dùng đọc & đánh dấu đã đọc (icon chuông).
**Thực thể quản lý:** `Notification`.
**Cơ chế:** Lắng nghe **domain event** từ module `meetings` qua EventBus (BullMQ), tạo 1 bản ghi cho mỗi User thuộc phòng ban liên quan.

```text
src/modules/notifications/
├── notifications.module.ts          # TypeOrmModule.forFeature([Notification]); bind INotificationRepository; inject IUserRepository (read-only) để fan-out; đăng ký event listener
│
├── presentation/
│   ├── notifications.controller.ts  # REST (JWT, theo user hiện tại):
│   │                                #   GET   /notifications          → danh sách thông báo của tôi (phân trang)
│   │                                #   PATCH /notifications/:id/read  → đánh dấu 1 thông báo đã đọc
│   │                                #   PATCH /notifications/read-all  → đánh dấu tất cả đã đọc
│   └── notifications.gateway.ts     # WebSocket Gateway: đẩy thông báo realtime tới icon chuông của User đang online (kênh giao 'notification')
│
├── application/
│   ├── command/
│   │   ├── mark-as-read.handler.ts  # Đặt is_read=true cho 1 thông báo (kiểm tra thuộc về user hiện tại)
│   │   └── mark-all-as-read.handler.ts # Đặt is_read=true cho mọi thông báo chưa đọc của user
│   ├── query/
│   │   └── list-notifications.handler.ts # Danh sách thông báo của user (mới nhất trước, kèm số chưa đọc)
│   ├── listeners/
│   │   └── meeting-events.listener.ts # CỐT LÕI: nghe MeetingCreated/StatusChanged/InfoUpdated → tạo 1 Notification cho mỗi User trong phòng ban
│   └── dto/
│       ├── list-notifications-query.dto.ts # Input: page, limit, is_read?
│       └── notification.dto.ts       # Output: id, type, message, is_read, meeting_id, created_at
│
├── domain/
│   ├── entities/
│   │   └── notification.entity.ts   # @Entity('notifications'): cột user_id, meeting_id, type(ENUM), message, is_read; method markRead(); factory tạo theo loại event
│   └── ports/
│       └── notification.repository.port.ts # INotificationRepository: bulkCreate, findByUser, markRead, markAllRead, countUnread
│
└── infrastructure/
    └── repositories/
        └── notification.repository.ts # Triển khai INotificationRepository qua Repository<Notification> (bulk fan-out theo phòng ban, phân trang)
```

---

## 9. Module `dashboard/` — Thống kê & báo cáo (chỉ đọc)

**Phạm vi:** Các báo cáo tổng hợp cho Admin: KPIs, xu hướng theo tháng, xếp hạng phòng ban, cuộc họp dài nhất, phân bổ nhân sự.
**Đặc thù:** Module **chỉ đọc (CQRS Query side)**, **không sở hữu entity riêng**; truy vấn tổng hợp xuyên `meetings`/`users`/`departments` qua **read-port** chuyên dụng để giữ ranh giới module.

```text
src/modules/dashboard/
├── dashboard.module.ts              # Bind IDashboardReadPort với impl đọc tổng hợp; chỉ expose query handler (không có command/entity ghi)
│
├── presentation/
│   └── dashboard.controller.ts      # REST quản trị (@Roles('ADMIN')):
│                                    #   GET /admin/dashboard/kpis                → tổng số họp, tổng giờ audio, số User active, TB giờ/cuộc họp
│                                    #   GET /admin/dashboard/trends              → biểu đồ 12 tháng (số họp, thời lượng), filter phòng ban
│                                    #   GET /admin/dashboard/department-ranking  → Top 5 phòng ban nhiều cuộc họp nhất
│                                    #   GET /admin/dashboard/longest-meetings    → Top 5 cuộc họp dài nhất, filter phòng ban
│                                    #   GET /admin/dashboard/staff-distribution  → tỷ trọng số User theo từng phòng ban
│
├── application/
│   ├── query/
│   │   ├── get-kpis.handler.ts              # Tổng hợp các chỉ số tổng quan theo bộ lọc tháng/năm
│   │   ├── get-trends.handler.ts           # Gom số liệu theo 12 tháng (có thể lọc phòng ban)
│   │   ├── get-department-ranking.handler.ts # Xếp hạng phòng ban theo số cuộc họp
│   │   ├── get-longest-meetings.handler.ts # Lấy Top 5 theo duration_seconds
│   │   └── get-staff-distribution.handler.ts # Đếm số User theo phòng ban
│   └── dto/
│       ├── dashboard-filter.dto.ts  # Input chung: month?, year?, department_id? (filter riêng cho trends & longest)
│       ├── kpis.dto.ts              # Output các thẻ chỉ số
│       ├── trend-point.dto.ts       # Output điểm dữ liệu theo tháng
│       └── ranking-item.dto.ts      # Output 1 dòng xếp hạng
│
├── domain/
│   └── ports/
│       └── dashboard-read.port.ts   # IDashboardReadPort: aggregateKpis, aggregateTrends, rankDepartments, topLongestMeetings, staffDistribution
│
└── infrastructure/
    └── repositories/
        └── dashboard-read.repository.ts # Triển khai IDashboardReadPort bằng truy vấn tổng hợp (GROUP BY, JOIN) tối ưu trên PostgreSQL
```

---

## 10. Phụ lục A — Bảng bind Port → Implementation (Dependency Inversion)

Mỗi Port (`domain`) được bind tới một implementation (`infrastructure`) qua DI token trong `*.module.ts`. Repository implementation **dùng trực tiếp `Repository<Entity>` của TypeORM** trên chính entity đã gắn decorator (không qua Mapper):

| Port (Domain) | Implementation (Infrastructure) | Module bind |
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

## 11. Phụ lục B — Bản đồ thực thể ↔ module sở hữu

Mỗi thực thể là **một class duy nhất** trong `domain/entities/` mang decorator TypeORM, ánh xạ thẳng tới bảng tương ứng:

| Thực thể (8) | Module sở hữu | File entity | Bảng vật lý |
| --- | --- | --- | --- |
| `User` | users | `user.entity.ts` | `users` |
| `Department` | departments | `department.entity.ts` | `departments` |
| `RefreshToken` | auth | `refresh-token.entity.ts` | `refresh_tokens` |
| `PasswordResetOTP` | auth | `password-reset-otp.entity.ts` | `password_reset_otps` |
| `Meeting` | meetings | `meeting.entity.ts` | `meetings` |
| `TranscriptBlock` | meetings | `transcript-block.entity.ts` | `transcript_blocks` |
| `MeetingSummary` | meetings | `meeting-summary.entity.ts` | `meeting_summaries` |
| `Notification` | notifications | `notification.entity.ts` | `notifications` |

---

## 12. Phụ lục C — Ghi chú phạm vi (Guardrails)

- **Domain Entity = ORM Entity:** thực thể nghiệp vụ dùng trực tiếp decorator TypeORM; lớp `domain` chấp nhận phụ thuộc TypeORM để tối ưu thời gian triển khai (không tách `orm-entity`, không Mapper).
- **KHÔNG** triển khai cộng tác chỉnh sửa đồng thời (scope MP1), **KHÔNG** widget nổi khi chạy nền, **KHÔNG** multi-tenant/quota (scope MP3).
- **KHÔNG** có luồng Register tự do; tài khoản do Admin tạo, mật khẩu ngẫu nhiên gửi qua email (`shared/mailer`).
- Logic nghiệp vụ chỉ nằm ở `application` & `domain`; `presentation`/`infrastructure` tuân thủ Dependency Inversion qua Ports.
- Ghi âm Live: file tạm local → finalize → upload MinIO/S3 → lưu URL → xóa file tạm (S3 không stream append).
- Xóa mềm xuyên suốt (`deleted_at` / `is_active` / `is_revoked`); không Hard Delete tài khoản hay dữ liệu cuộc họp lịch sử.
- Phòng ban còn nhân sự **không được** xóa mềm (chặn tại `delete-department.handler.ts`).
