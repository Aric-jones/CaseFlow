package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.UiElement;
import com.caseflow.entity.ui.UiPage;
import com.caseflow.mapper.ui.UiElementMapper;
import com.caseflow.mapper.ui.UiPageMapper;
import com.caseflow.service.ui.UiPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UiPageServiceImpl extends ServiceImpl<UiPageMapper, UiPage> implements UiPageService {

    private final UiElementMapper elementMapper;

    @Override
    public Page<UiPage> listByProject(String projectId, String directoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<UiPage> qw = new LambdaQueryWrapper<UiPage>()
                .eq(UiPage::getProjectId, projectId)
                .eq(UiPage::getDeleted, 0);
        if (directoryId != null && !directoryId.isBlank()) {
            qw.eq(UiPage::getDirectoryId, directoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UiPage::getName, keyword);
        }
        qw.orderByAsc(UiPage::getSortOrder).orderByDesc(UiPage::getCreatedAt);

        Page<UiPage> result = page(new Page<>(page, size), qw);
        for (UiPage p : result.getRecords()) {
            Long cnt = elementMapper.selectCount(
                    new LambdaQueryWrapper<UiElement>().eq(UiElement::getPageId, p.getId()));
            p.setElementCount(cnt.intValue());
        }
        return result;
    }

    @Override
    public UiPage getDetail(String id) {
        UiPage p = getById(id);
        if (p == null) return null;
        Long cnt = elementMapper.selectCount(
                new LambdaQueryWrapper<UiElement>().eq(UiElement::getPageId, p.getId()));
        p.setElementCount(cnt.intValue());
        return p;
    }

    @Override
    public List<String> getAllTags(String projectId) {
        List<UiPage> pages = list(new LambdaQueryWrapper<UiPage>()
                .eq(UiPage::getProjectId, projectId)
                .eq(UiPage::getDeleted, 0)
                .isNotNull(UiPage::getTags));
        Set<String> tagSet = new HashSet<>();
        for (UiPage p : pages) {
            if (p.getTags() != null) tagSet.addAll(p.getTags());
        }
        return new ArrayList<>(tagSet);
    }

    @Override
    public boolean hasElements(String id) {
        return elementMapper.selectCount(
                new LambdaQueryWrapper<UiElement>().eq(UiElement::getPageId, id)) > 0;
    }
}
