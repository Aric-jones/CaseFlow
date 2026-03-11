package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public Result<List<ReviewAssignment>> list(@RequestParam Long caseSetId) {
        return Result.ok(reviewService.getReviewers(caseSetId));
    }

    @PutMapping("/{id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        reviewService.updateReviewStatus(id, status);
        return Result.ok();
    }
}
