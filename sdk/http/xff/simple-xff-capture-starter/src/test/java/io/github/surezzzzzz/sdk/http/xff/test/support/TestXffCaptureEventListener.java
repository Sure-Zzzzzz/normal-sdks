package io.github.surezzzzzz.sdk.http.xff.test.support;

import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试用 XFF Capture 事件监听器。
 *
 * @author surezzzzzz
 */
@Component
public class TestXffCaptureEventListener {

    private final List<XffCaptureEvent> eventList = Collections.synchronizedList(new ArrayList<>());

    /**
     * 接收测试事件。
     *
     * @param event XFF Capture 事件
     */
    @EventListener
    public void onEvent(XffCaptureEvent event) {
        eventList.add(event);
    }

    /**
     * 获取当前事件快照。
     *
     * @return 事件列表副本
     */
    public List<XffCaptureEvent> snapshot() {
        synchronized (eventList) {
            return new ArrayList<>(eventList);
        }
    }

    /**
     * 清空已接收事件。
     */
    public void clear() {
        eventList.clear();
    }
}
