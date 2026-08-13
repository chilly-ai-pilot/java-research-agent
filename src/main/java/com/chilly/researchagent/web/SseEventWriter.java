package com.chilly.researchagent.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 手写 SSE 帧并立即 flush，避免容器缓冲导致客户端一次性收到全部事件。 */
final class SseEventWriter {

    private SseEventWriter() {
    }

    static void send(OutputStream outputStream, String eventName, String data) throws IOException {
        StringBuilder frame = new StringBuilder();
        if (eventName != null && !eventName.isBlank()) {
            frame.append("event:").append(eventName).append('\n');
        }
        if (data != null) {
            for (String line : data.split("\n", -1)) {
                frame.append("data:").append(line).append('\n');
            }
        }
        frame.append('\n');
        outputStream.write(frame.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
