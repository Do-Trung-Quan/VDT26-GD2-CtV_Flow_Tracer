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

    private final List<Pattern> includes;
    private final List<Pattern> excludes;
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
