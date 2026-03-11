package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.CustomAttribute;
import java.util.List;
public interface CustomAttributeService extends IService<CustomAttribute> {
    List<CustomAttribute> listByProject(String projectId);
}
