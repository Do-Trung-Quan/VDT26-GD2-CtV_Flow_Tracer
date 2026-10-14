### **2.3. Phân tích kiến trúc hệ thống**

Hệ thống được thiết kế theo kiến trúc nguyên khối tổ chức theo chức năng (*Package-by-feature*), kết hợp phân tách lõi giải thuật độc lập và cơ chế tiến trình phụ trợ (*Sidecar CLI*), toàn bộ được đóng gói và vận hành cục bộ trong môi trường Docker Compose. Kiến trúc tổng thể được chia thành các tầng thành phần với trách nhiệm và ranh giới rõ ràng như sau:

**Tầng Giao diện người dùng (Client Layer)**
• Công nghệ: Angular phiên bản 21 (kiến trúc Standalone Components, cơ chế quản lý trạng thái phản ứng bằng Signals), Angular Material, CDK và bộ dựng đồ họa véc-tơ SVG tự phát triển.  
• Nhiệm vụ: Xây dựng không gian làm việc trực quan theo bố cục ba cột tương tác: cột trái duyệt cây thành phần giao diện và cây điểm nhận yêu cầu máy chủ; khung vẽ trung tâm trực quan hóa biểu đồ tuần tự tương tác (hỗ trợ phóng to, thu nhỏ, kéo di chuyển, xuất định dạng PlantUML và hình ảnh đồ họa); cột phải hiển thị tab Diễn giải và tab Kịch bản từng bước đồng bộ.  
• Giao thức: Giao tiếp với máy chủ qua giao thức HTTP/REST chuẩn hóa cho các tác vụ quản trị, truy vấn cây thành phần, dữ liệu dò vết và cơ chế truyền dữ liệu dạng luồng (Server-Sent Events) để cập nhật tiến độ quét mã nguồn cùng nội dung diễn giải theo thời gian thực tại địa chỉ nội bộ (`127.0.0.1`).

**Tầng Trình diễn và Điều phối dịch vụ (Presentation & Application Layer)**
• Công nghệ: Java 21, Spring Boot (Spring Web MVC, Jakarta Bean Validation, Springdoc OpenAPI).  
• Nhiệm vụ: Tiếp nhận, phân giải và kiểm tra tính hợp lệ của các yêu cầu từ giao diện; quản lý và điều phối các dịch vụ nghiệp vụ chính (quản lý không gian làm việc, quản lý mã nguồn, khám phá cấu trúc, liên kết giao diện – máy chủ, dò vết và diễn giải). Tầng này điều phối đường ống quét mã nguồn qua sáu giai đoạn tuần tự (*Discover → Diff → Extract → Resolve → Persist → Link*), đồng thời áp dụng công cụ ArchUnit để tự động kiểm soát các quy tắc kiến trúc và ngăn chặn vi phạm phụ thuộc giữa các khối chức năng.

**Tầng Lõi phân tích tĩnh và Dò vết (Analysis Core & Tracing Layer)**
• Công nghệ: Java thuần (gồm các mô-đun Maven độc lập: *flowtracer-ir*, *flowtracer-core*, *flowtracer-adapter-spring*), thư viện JavaParser, cơ chế luồng ảo (*Virtual Threads*).  
• Nhiệm vụ: Hoạt động hoàn toàn độc lập với Spring Framework và cơ sở dữ liệu để bảo đảm tính tất định; phân tích cú pháp tĩnh mã nguồn Java/Spring Boot, giải quyết cơ chế tiêm phụ thuộc (*Dependency Injection*) và nhận diện các tác vụ truy vấn dữ liệu. Tầng này đảm nhiệm thuật toán liên kết đường dẫn (*Linker*) giữa hai nền tảng, thuật toán dò vết luồng nghiệp vụ (*Tracer*) trên đồ thị với cơ chế kiểm soát vòng lặp (*CycleGuard*), tự động bóc tách các khối điều kiện rẽ nhánh (*alt*, *opt*) và chuyển đổi kết quả thành mô hình hiển thị biểu đồ tuần tự cùng kịch bản khung.

**Tầng Tiến trình phân tích phụ trợ (Sidecar Analyzer Layer)**
• Công nghệ: Node.js 22, TypeScript Compiler API, `@angular/compiler` (`parseTemplate`).  
• Nhiệm vụ: Đóng vai trò là tiến trình dòng lệnh không trạng thái được máy chủ kích hoạt khi cần phân tích mã nguồn giao diện Angular. Tiến trình bóc tách cấu trúc thành phần, cấu hình định tuyến (*routes*), bảng ký hiệu (*SymbolTable*), các bộ lắng nghe sự kiện trên màn hình và các điểm gọi dịch vụ mạng (*HttpClient*), sau đó chuyển đổi kết quả thành phân mảnh đồ thị trung gian định dạng JSON trả về cho máy chủ theo hợp đồng dữ liệu chuẩn hóa (*contracts/*).

**Tầng Dữ liệu và Mô hình đồ thị trung gian (Database & IR Layer)**
• Công nghệ: Hệ quản trị cơ sở dữ liệu PostgreSQL 16, Spring Data JPA, `JdbcClient`, Flyway.  
• Nhiệm vụ: Lưu trữ vĩnh viễn cấu hình không gian làm việc, danh mục tệp tin, lịch sử quét, các liên kết chốt tay và toàn bộ mô hình đồ thị trung gian độc lập công nghệ (gồm bảng đỉnh `ir_nodes` và bảng cạnh `ir_edges`). Tầng này sử dụng `JdbcClient` để thực thi cơ chế ghi dữ liệu theo lô lớn (*batch write*) nhằm tối ưu hóa hiệu năng lưu trữ đồ thị hàng chục nghìn bản ghi, kết hợp kiểu dữ liệu JSONB và chỉ mục tìm kiếm toàn văn (*tsvector*) để phục vụ tra cứu nhanh chóng.

**Tầng Cổng giao tiếp Trí tuệ nhân tạo (AI Gateway Layer)**
• Công nghệ: Spring AI (`ChatClient`) kết nối mô hình Google Gemini.  
• Nhiệm vụ: Đóng vai trò cầu nối an toàn hỗ trợ các mục đích phụ trợ: tóm tắt mục đích nghiệp vụ (80–100 từ), làm mượt lời kể kịch bản từng bước, sinh dữ liệu mẫu minh họa từ cấu trúc đối tượng truyền tải dữ liệu và phân tích lý do khi liên kết chưa chắc chắn. Tầng này tích hợp bộ lọc tự động che giấu thông tin nhạy cảm (*SecretRedactor*), lưu trữ đệm phản hồi theo mã băm đầu vào, kiểm duyệt cấu trúc dữ liệu đầu ra qua JSON Schema và duy trì bộ sinh dữ liệu dự phòng tất định giúp hệ thống vận hành bình thường ngay cả khi không có kết nối mạng.

**Hệ thống ngoài và Nguồn mã phân tích (External Systems & Source Code)**
• Thành phần: Thư mục mã nguồn dự án trên máy tính cá nhân (*SOURCE_ROOT*) và dịch vụ mô hình ngôn ngữ lớn (Google Gemini API).  
• Nhiệm vụ: Thư mục mã nguồn dự án được gắn kết vào ứng dụng theo quyền chỉ đọc (*read-only*) để phục vụ phân tích tĩnh mà không biên dịch, không cài đặt thư viện ngoài và không can thiệp vào mã nguồn gốc hay cơ sở dữ liệu thực tế của dự án; dịch vụ Google Gemini tiếp nhận các trích đoạn văn bản kỹ thuật đã được làm sạch để trả về nội dung diễn giải bổ trợ.

*(Hình 2. Sơ đồ kiến trúc phân tầng của hệ thống)*