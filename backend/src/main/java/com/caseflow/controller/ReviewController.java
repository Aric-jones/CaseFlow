package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reviews") @RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping public Result<?> list(@RequestParam String caseSetId) {
        return Result.ok(reviewService.lambdaQuery().eq(ReviewAssignment::getCaseSetId, caseSetId).list());
    }
    @PutMapping("/{id}") public Result<?> updateStatus(@PathVariable String id, @RequestParam String status) {
        ReviewAssignment ra = reviewService.getById(id); if (ra != null) { ra.setStatus(status); reviewService.updateById(ra); }
        return Result.ok();
    }
}
