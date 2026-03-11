package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ReviewAssignment;
import java.util.List;

public interface ReviewService extends IService<ReviewAssignment> {
    List<ReviewAssignment> getReviewers(Long caseSetId);
    void updateReviewStatus(Long id, String status);
}
