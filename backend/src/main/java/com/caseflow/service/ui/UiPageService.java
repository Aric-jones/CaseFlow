package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiPage;

import java.util.List;

public interface UiPageService extends IService<UiPage> {
    Page<UiPage> listByProject(String projectId, String directoryId, String keyword, int page, int size);
    UiPage getDetail(String id);
    List<String> getAllTags(String projectId);
    boolean hasElements(String id);
}
