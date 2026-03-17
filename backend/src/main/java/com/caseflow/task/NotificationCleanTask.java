package com.caseflow.task;

import com.caseflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("notificationCleanTask")
@RequiredArgsConstructor
public class NotificationCleanTask {

    private final NotificationService notificationService;

    public String execute() {
        int deleted = notificationService.deleteOlderThan(30);
        String msg = "清理了 " + deleted + " 条30天前的消息";
        log.info("[定时任务] {}", msg);
        return msg;
    }
}
