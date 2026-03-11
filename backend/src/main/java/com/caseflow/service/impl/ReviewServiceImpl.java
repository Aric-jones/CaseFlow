package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.mapper.ReviewAssignmentMapper;
import com.caseflow.service.ReviewService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewAssignmentMapper, ReviewAssignment> implements ReviewService {

    @Override
    public List<ReviewAssignment> getReviewers(Long caseSetId) {
        return this.lambdaQuery().eq(ReviewAssignment::getCaseSetId, caseSetId).list();
    }

    @Override
    public void updateReviewStatus(Long id, String status) {
        ReviewAssignment ra = getById(id);
        if (ra == null) throw new BusinessException("评审记录不存在");
        if (!ra.getReviewerId().equals(CurrentUserUtil.getCurrentUserId())) {
            throw new BusinessException("只能修改自己的评审状态");
        }
        ra.setStatus(status);
        this.updateById(ra);
    }
}
