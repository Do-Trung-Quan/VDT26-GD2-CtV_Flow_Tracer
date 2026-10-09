package com.ctv.flowtracer.core.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Áp luật include/exclude (glob) và ngưỡng kích thước cho từng file của Source.
 *
 * <p>Glob luôn so với đường dẫn tương đối dùng dấu '/', độc lập hệ điều hành: {@code **} khớp nhiều cấp thư mục
 * (kể cả không cấp nào), {@code *} khớp trong một cấp, {@code ?} khớp một ký tự.
 */
public final class FileFilter {

    /** Tên file giả không thể tồn tại, dùng để kiểm tra luật loại trừ có phủ mọi file trong thư mục không. */
    private static final String DIRECTORY_PROBE = "\u0000";

    private final List<Pattern> includes;
    private final List<Pattern> excludes;
    /** Luật loại trừ không có dạng {@code …/**} — luật dạng đó đã được áp ở cấp thư mục khi duyệt có cắt tỉa. */
    private final List<Pattern> fileOnlyExcludes;
    private final long maxBytes;

    /**
     * @param includeGlobs rỗng nghĩa là lấy tất cả
     * @param excludeGlobs luật loại trừ thủ công; {@link DefaultExcludes#GLOBS} luôn được cộng thêm
     * @param maxBytes kích thước tối đa của một file
     */
    public FileFilter(List<String> includeGlobs, List<String> excludeGlobs, long maxBytes) {
        this.includes = includeGlobs.stream().map(FileFilter::toRegex).toList();
        var allExcludes = new ArrayList<>(DefaultExcludes.GLOBS);
        allExcludes.addAll(excludeGlobs);
        this.excludes = allExcludes.stream().map(FileFilter::toRegex).toList();
        this.fileOnlyExcludes = allExcludes.stream().filter(g -> !g.endsWith("/**")).map(FileFilter::toRegex).toList();
        this.maxBytes = maxBytes;
    }

    /** @param relativePath đường dẫn tương đối trong Source, dùng dấu '/' */
    public boolean accept(String relativePath, long sizeBytes) {
        if (sizeBytes > maxBytes) {
            return false;
        }
        String path = relativePath.replace('\\', '/');
        if (excludes.stream().anyMatch(p -> p.matcher(path).matches())) {
            return false;
        }
        return includes.isEmpty() || includes.stream().anyMatch(p -> p.matcher(path).matches());
    }

    /**
     * Như {@link #accept} nhưng dùng khi duyệt cây đã cắt tỉa bằng {@link #acceptDirectory}: bỏ qua các luật dạng
     * {@code …/**} vì thư mục chứa file đã được chấp nhận (luật đó khớp file thì cũng khớp thư mục cha). Nhanh hơn nhiều
     * với cây lớn.
     */
    public boolean acceptInWalkedDirectory(String relativePath, long sizeBytes) {
        if (sizeBytes > maxBytes) {
            return false;
        }
        String path = relativePath.replace('\\', '/');
        if (fileOnlyExcludes.stream().anyMatch(p -> p.matcher(path).matches())) {
            return false;
        }
        return includes.isEmpty() || includes.stream().anyMatch(p -> p.matcher(path).matches());
    }

    /**
     * Có duyệt vào thư mục này không: bỏ cả nhánh khi một luật loại trừ phủ toàn bộ thư mục (vd {@code **}{@code /node_modules/**},
     * {@code docs/**}). Thư mục chỉ bị loại một phần thì vẫn duyệt, file được lọc riêng bằng {@link #accept}.
     *
     * @param relativeDir đường dẫn thư mục tương đối trong Source, dùng dấu '/'
     */
    public boolean acceptDirectory(String relativeDir) {
        if (relativeDir.isEmpty()) {
            return true;
        }
        String probe = relativeDir.replace('\\', '/') + "/" + DIRECTORY_PROBE;
        return excludes.stream().noneMatch(p -> p.matcher(probe).matches());
    }

    /** Nhận biết file nhị phân dựa trên vài KB đầu: có byte 0 thì coi là nhị phân. */
    public static boolean looksBinary(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    static Pattern toRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                boolean slashAfter = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                sb.append(slashAfter ? "(?:.*/)?" : ".*");
                i += slashAfter ? 2 : 1;
            } else if (c == '*') {
                sb.append("[^/]*");
            } else if (c == '?') {
                sb.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return Pattern.compile(sb.toString());
    }
}
