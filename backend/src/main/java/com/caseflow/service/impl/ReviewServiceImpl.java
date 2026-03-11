package com.caseflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ReviewAssignment;
import com.caseflow.mapper.ReviewAssignmentMapper;
import com.caseflow.service.ReviewService;
import org.springframework.stereotype.Service;
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewAssignmentMapper, ReviewAssignment> implements ReviewService {}
