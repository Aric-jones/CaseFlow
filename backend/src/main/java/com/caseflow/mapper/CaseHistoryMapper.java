package com.caseflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.CaseHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CaseHistoryMapper extends BaseMapper<CaseHistory> {
}
