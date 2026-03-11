package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.CustomAttributeDTO;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.mapper.CustomAttributeMapper;
import com.caseflow.service.CustomAttributeService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomAttributeServiceImpl extends ServiceImpl<CustomAttributeMapper, CustomAttribute> implements CustomAttributeService {

    @Override
    public List<CustomAttribute> listByProject(Long projectId) {
        return this.lambdaQuery()
                .eq(CustomAttribute::getProjectId, projectId)
                .orderByAsc(CustomAttribute::getSortOrder)
                .list();
    }

    @Override
    public CustomAttribute createAttribute(CustomAttributeDTO dto) {
        CustomAttribute attr = new CustomAttribute();
        attr.setProjectId(dto.getProjectId());
        attr.setName(dto.getName());
        attr.setOptions(dto.getOptions());
        attr.setMultiSelect(dto.getMultiSelect() != null ? dto.getMultiSelect() : 0);
        attr.setNodeTypeLimit(dto.getNodeTypeLimit());
        attr.setDisplayType(dto.getDisplayType() != null ? dto.getDisplayType() : "DROPDOWN");
        attr.setSortOrder(0);
        this.save(attr);
        return attr;
    }

    @Override
    public CustomAttribute updateAttribute(Long id, CustomAttributeDTO dto) {
        CustomAttribute attr = getById(id);
        if (attr == null) throw new BusinessException("属性不存在");
        if (dto.getName() != null) attr.setName(dto.getName());
        if (dto.getOptions() != null) attr.setOptions(dto.getOptions());
        if (dto.getMultiSelect() != null) attr.setMultiSelect(dto.getMultiSelect());
        if (dto.getNodeTypeLimit() != null) attr.setNodeTypeLimit(dto.getNodeTypeLimit());
        if (dto.getDisplayType() != null) attr.setDisplayType(dto.getDisplayType());
        this.updateById(attr);
        return attr;
    }

    @Override
    public void deleteAttribute(Long id) {
        this.removeById(id);
    }
}
