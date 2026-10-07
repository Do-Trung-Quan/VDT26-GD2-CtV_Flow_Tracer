package com.ctv.flowtracer.core.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Tính SHA-256 (hex) — cơ sở của quét lại tăng dần và cache dữ kiện. */
public final class ContentHash {

    private ContentHash() {}

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }

    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }
}
