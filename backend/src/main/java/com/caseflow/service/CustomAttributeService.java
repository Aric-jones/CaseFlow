package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.CustomAttributeDTO;
import com.caseflow.entity.CustomAttribute;
import java.util.List;

public interface CustomAttributeService extends IService<CustomAttribute> {
    List<CustomAttribute> listByProject(Long projectId);
    CustomAttribute createAttribute(CustomAttributeDTO dto);
    CustomAttribute updateAttribute(Long id, CustomAttributeDTO dto);
    void deleteAttribute(Long id);
}
