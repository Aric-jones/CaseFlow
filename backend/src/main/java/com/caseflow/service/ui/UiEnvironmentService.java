package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiEnvironment;
import java.util.List;

public interface UiEnvironmentService extends IService<UiEnvironment> {
    List<UiEnvironment> listByProject(String projectId);
}
