package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.NotificationService;
import com.caseflow.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/reviews") @RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final CaseSetService caseSetService;
    private final NotificationService notificationService;

    @GetMapping
    public Result<?> list(@RequestParam String caseSetId) {
        return Result.ok(reviewService.lambdaQuery().eq(ReviewAssignment::getCaseSetId, caseSetId).list());
    }

    @SaCheckPermission("review:approve")
    @Transactional
    @PutMapping("/{id}")
    public Result<?> updateStatus(@PathVariable String id, @RequestParam String status) {
        ReviewAssignment ra = reviewService.getById(id);
        if (ra == null) return Result.error("评审记录不存在");
        ra.setStatus(status);
        reviewService.updateById(ra);

        List<ReviewAssignment> all = reviewService.lambdaQuery()
                .eq(ReviewAssignment::getCaseSetId, ra.getCaseSetId()).list();
        boolean allApproved = !all.isEmpty() && all.stream().allMatch(r -> "APPROVED".equals(r.getStatus()));

        CaseSet cs = caseSetService.getById(ra.getCaseSetId());
        if (allApproved) {
            if (cs != null && ("PENDING_REVIEW".equals(cs.getStatus()) || "APPROVED".equals(cs.getStatus()))) {
                cs.setStatus("APPROVED");
                caseSetService.updateById(cs);
            }
        } else {
            if (cs != null && "APPROVED".equals(cs.getStatus())) {
                cs.setStatus("PENDING_REVIEW");
                caseSetService.updateById(cs);
            }
        }

        // 通知用例集创建人评审状态变化
        if (cs != null && cs.getCreatedBy() != null) {
            String reviewerName = CurrentUserUtil.getCurrentUserDisplayName();
            String link = "/review/" + cs.getId();
            String statusCn = reviewStatusCn(status);
            // 每次单个评审人状态变化都通知创建人
            notificationService.send(cs.getCreatedBy(), "REVIEW_STATUS_CHANGE",
                    "评审状态变更",
                    reviewerName + " 将用例集「" + cs.getName() + "」的评审状态更改为 " + statusCn, link);
            // 全部评审人都通过时，额外发一条"评审通过"
            if (allApproved) {
                notificationService.send(cs.getCreatedBy(), "REVIEW_APPROVED",
                        "评审通过", "用例集「" + cs.getName() + "」的评审已全部通过", link);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allApproved", allApproved);
        result.put("caseSetStatus", cs != null ? cs.getStatus() : null);
        result.put("reviewers", all);
        return Result.ok(result);
    }

    private String reviewStatusCn(String status) {
        return switch (status) {
            case "APPROVED" -> "通过";
            case "REJECTED" -> "不通过";
            case "NEED_MODIFICATION" -> "待修改";
            case "PENDING" -> "待评审";
            default -> status;
        };
    }
}
