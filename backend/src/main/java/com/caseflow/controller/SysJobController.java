package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caseflow.common.BusinessException;
import com.caseflow.common.Result;
import com.caseflow.entity.SysJob;
import com.caseflow.entity.SysJobLog;
import com.caseflow.mapper.SysJobLogMapper;
import com.caseflow.mapper.SysJobMapper;
import com.caseflow.task.ScheduledTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sys-jobs")
@RequiredArgsConstructor
@SaCheckPermission("settings:*")
public class SysJobController {

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final ScheduledTaskManager taskManager;

    @GetMapping
    public Result<?> list() {
        return Result.ok(jobMapper.selectList(new LambdaQueryWrapper<SysJob>().orderByAsc(SysJob::getCreatedAt)));
    }

    @PostMapping
    public Result<?> create(@RequestBody SysJob job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) throw new BusinessException("任务名称不能为空");
        if (job.getInvokeTarget() == null || job.getInvokeTarget().isBlank()) throw new BusinessException("调用目标不能为空");
        if (job.getCronExpression() == null || job.getCronExpression().isBlank()) throw new BusinessException("cron表达式不能为空");
        if (job.getStatus() == null) job.setStatus(0);
        jobMapper.insert(job);
        if (job.getStatus() == 0) taskManager.addTask(job);
        return Result.ok(job);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody SysJob job) {
        SysJob existing = jobMapper.selectById(id);
        if (existing == null) throw new BusinessException("任务不存在");
        existing.setJobName(job.getJobName());
        existing.setJobGroup(job.getJobGroup());
        existing.setInvokeTarget(job.getInvokeTarget());
        existing.setCronExpression(job.getCronExpression());
        existing.setStatus(job.getStatus());
        existing.setRemark(job.getRemark());
        jobMapper.updateById(existing);
        if (existing.getStatus() != null && existing.getStatus() == 0) {
            taskManager.addTask(existing);
        } else {
            taskManager.removeTask(id);
        }
        return Result.ok(existing);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        taskManager.removeTask(id);
        jobMapper.deleteById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<?> changeStatus(@PathVariable String id, @RequestParam Integer status) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) throw new BusinessException("任务不存在");
        job.setStatus(status);
        jobMapper.updateById(job);
        if (status == 0) {
            taskManager.addTask(job);
        } else {
            taskManager.removeTask(id);
        }
        return Result.ok();
    }

    @PostMapping("/{id}/run")
    public Result<?> runOnce(@PathVariable String id) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) throw new BusinessException("任务不存在");
        taskManager.runOnce(job);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<?> logs(@PathVariable String id,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(jobLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysJobLog>()
                        .eq(SysJobLog::getJobId, id)
                        .orderByDesc(SysJobLog::getStartTime)));
    }
}
