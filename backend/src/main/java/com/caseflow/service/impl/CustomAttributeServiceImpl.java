package com.caseflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.mapper.CustomAttributeMapper;
import com.caseflow.service.CustomAttributeService;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class CustomAttributeServiceImpl extends ServiceImpl<CustomAttributeMapper, CustomAttribute> implements CustomAttributeService {
    @Override
    public List<CustomAttribute> listByProject(String projectId) {
        return lambdaQuery().eq(CustomAttribute::getProjectId, projectId).orderByAsc(CustomAttribute::getSortOrder).list();
    }
}
