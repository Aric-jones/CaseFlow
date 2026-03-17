package com.caseflow.task;

import com.caseflow.entity.SysJob;
import com.caseflow.entity.SysJobLog;
import com.caseflow.mapper.SysJobLogMapper;
import com.caseflow.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskManager {

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final ApplicationContext applicationContext;
    private final TaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<SysJob> jobs = jobMapper.selectList(null);
        for (SysJob job : jobs) {
            if (job.getStatus() != null && job.getStatus() == 0) {
                addTask(job);
            }
        }
        log.info("[ScheduledTaskManager] 已加载 {} 个定时任务", runningTasks.size());
    }

    public void addTask(SysJob job) {
        removeTask(job.getId());
        try {
            Runnable runnable = createRunnable(job);
            ScheduledFuture<?> future = taskScheduler.schedule(runnable, new CronTrigger(job.getCronExpression()));
            runningTasks.put(job.getId(), future);
            log.info("[ScheduledTaskManager] 启动任务: {} cron={}", job.getJobName(), job.getCronExpression());
        } catch (Exception e) {
            log.error("[ScheduledTaskManager] 启动任务失败: {}", job.getJobName(), e);
        }
    }

    public void removeTask(String jobId) {
        ScheduledFuture<?> future = runningTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void runOnce(SysJob job) {
        try {
            Runnable runnable = createRunnable(job);
            runnable.run();
        } catch (Exception e) {
            log.error("[ScheduledTaskManager] 立即执行失败: {}", job.getJobName(), e);
        }
    }

    private Runnable createRunnable(SysJob job) {
        return () -> {
            LocalDateTime startTime = LocalDateTime.now();
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobId(job.getId());
            jobLog.setJobName(job.getJobName());
            jobLog.setInvokeTarget(job.getInvokeTarget());
            jobLog.setStartTime(startTime);
            try {
                String result = invokeTarget(job.getInvokeTarget());
                jobLog.setStatus(0);
                jobLog.setMessage(result);
            } catch (Exception e) {
                jobLog.setStatus(1);
                jobLog.setMessage("执行失败");
                jobLog.setException(e.getMessage());
                log.error("[ScheduledTask] 任务执行异常: {}", job.getJobName(), e);
            }
            jobLog.setEndTime(LocalDateTime.now());
            jobLogMapper.insert(jobLog);
        };
    }

    private String invokeTarget(String target) throws Exception {
        String[] parts = target.split("\\.");
        if (parts.length != 2) throw new IllegalArgumentException("invokeTarget格式错误: " + target);
        String beanName = parts[0];
        String methodName = parts[1];
        Object bean = applicationContext.getBean(beanName);
        Method method = bean.getClass().getMethod(methodName);
        Object result = method.invoke(bean);
        return result != null ? result.toString() : "执行完成";
    }
}
