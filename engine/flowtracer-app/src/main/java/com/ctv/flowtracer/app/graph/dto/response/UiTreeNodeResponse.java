package com.ctv.flowtracer.app.graph.dto.response;

import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;
import java.util.UUID;

/**
 * Một nút trên cây thành phần Frontend ({@code Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử}).
 *
 * @param nodeKey định danh ổn định của thành phần
 * @param kind loại: {@code UI_APP}, {@code UI_SCREEN}, {@code UI_TAB}, {@code UI_DIALOG}, {@code UI_FORM}, {@code UI_ELEMENT}
 * @param name tên kỹ thuật (tên ứng dụng, tên class component, tên hàm xử lý hoặc thẻ HTML)
 * @param displayName nhãn tiếng Việt đọc được từ mã nguồn (chữ trên nút, tiêu đề, route title); null nếu mã nguồn không có
 * @param fallbackName tên kỹ thuật lấy từ mã nguồn khi phần tử không có nhãn đọc được (null nếu đã có {@code displayName}
 *     hoặc không phải phần tử); thứ tự: tên control của ô nhập, tên hàm xử lý đầu tiên, khóa dịch chưa tra ra chữ, thẻ HTML
 * @param elementType loại phần tử ({@code button}, {@code input}, {@code select}, {@code link}…); chỉ có với {@code UI_ELEMENT}
 * @param callsApi phần tử: true nếu thao tác dẫn tới một lời gọi HTTP (qua hàm xử lý, facade, repository), false nếu chỉ thay đổi
 *     giao diện; màn hình / hộp thoại / ứng dụng: true nếu chính nó gọi HTTP khi được mở (hàm vòng đời) — KHÔNG tính phần tử bên
 *     trong; tab và form: null
 * @param leadsToApi chỉ có với phần tử: true nếu thao tác mở một hộp thoại hoặc chuyển tới một màn hình mà bên trong có gọi API (chính nó
 *     có thể không gọi API) — vẫn là một bước của nghiệp vụ nên giao diện giữ lại khi ẩn phần tử chỉ thao tác giao diện; null với loại khác
 * @param routePath đường dẫn route đầy đủ; chỉ có với {@code UI_SCREEN}
 * @param sourceId Source chứa ứng dụng; chỉ có với {@code UI_APP}
 * @param sourceName tên Source; chỉ có với {@code UI_APP}
 * @param filePath file khai báo, tương đối trong Source (template hoặc class)
 * @param line dòng bắt đầu
 * @param children các thành phần con theo đúng thứ tự trong template
 */
public record UiTreeNodeResponse(
        String nodeKey,
        NodeKind kind,
        String name,
        String displayName,
        String fallbackName,
        String elementType,
        Boolean callsApi,
        Boolean leadsToApi,
        String routePath,
        UUID sourceId,
        String sourceName,
        String filePath,
        Integer line,
        List<UiTreeNodeResponse> children) {}
