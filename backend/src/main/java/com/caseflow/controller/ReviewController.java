package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.service.CaseSetService;
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

    @GetMapping
    public Result<?> list(@RequestParam String caseSetId) {
        return Result.ok(reviewService.lambdaQuery().eq(ReviewAssignment::getCaseSetId, caseSetId).list());
    }

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

        Map<String, Object> result = new HashMap<>();
        result.put("allApproved", allApproved);
        result.put("caseSetStatus", cs != null ? cs.getStatus() : null);
        result.put("reviewers", all);
        return Result.ok(result);
    }
}
