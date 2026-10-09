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
 * Quản lý SseEmitter theo kênh (vd {@code scan:<id>}, {@code explanation:<key>}). Tự gỡ emitter khi client ngắt. Client
 * vào sau nhận ngay sự kiện gần nhất của kênh (vd tiến độ hiện tại của lần quét).
 */
@Component
public class SseHub {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, List<SseEmitter>> channels = new ConcurrentHashMap<>();
    private final Map<String, Event> lastEvents = new ConcurrentHashMap<>();

    private record Event(String name, Object data) {}

    public SseEmitter subscribe(String channel) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        channels.computeIfAbsent(channel, c -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> channels.getOrDefault(channel, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        Event last = lastEvents.get(channel);
        if (last != null) {
            send(emitter, last);
        }
        return emitter;
    }

    public void publish(String channel, String eventName, Object data) {
        Event event = new Event(eventName, data);
        lastEvents.put(channel, event);
        for (SseEmitter emitter : channels.getOrDefault(channel, List.of())) {
            send(emitter, event);
        }
    }

    /** Đóng kênh: kết thúc mọi kết nối và quên sự kiện gần nhất. */
    public void complete(String channel) {
        lastEvents.remove(channel);
        List<SseEmitter> emitters = channels.remove(channel);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }

    private static void send(SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name(event.name()).data(event.data()));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
