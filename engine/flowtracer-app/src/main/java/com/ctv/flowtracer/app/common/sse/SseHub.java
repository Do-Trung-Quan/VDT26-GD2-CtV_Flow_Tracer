package com.ctv.flowtracer.app.common.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Quản lý SseEmitter theo kênh (vd {@code scan:<id>}, {@code explanation:<key>}). Tự gỡ emitter khi client ngắt.
 *
 * <p>TODO (phase quét): heartbeat định kỳ và phát lại sự kiện cuối cho client vào sau.
 */
@Component
public class SseHub {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, List<SseEmitter>> channels = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String channel) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        channels.computeIfAbsent(channel, c -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> channels.getOrDefault(channel, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    public void publish(String channel, String eventName, Object data) {
        for (SseEmitter emitter : channels.getOrDefault(channel, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    public void complete(String channel) {
        List<SseEmitter> emitters = channels.remove(channel);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }
}
