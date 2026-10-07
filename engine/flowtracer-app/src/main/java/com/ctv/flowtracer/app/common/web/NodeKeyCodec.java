package com.ctv.flowtracer.app.common.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Mã hóa/giải mã node_key khi đặt trên đường dẫn URL (key chứa ':', '#', '/', khoảng trắng…). */
public final class NodeKeyCodec {

    private NodeKeyCodec() {}

    public static String encode(String nodeKey) {
        return URLEncoder.encode(nodeKey, StandardCharsets.UTF_8);
    }

    public static String decode(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
