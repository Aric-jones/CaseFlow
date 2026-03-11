package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseHistory;
import com.caseflow.mapper.CaseHistoryMapper;
import com.caseflow.service.CaseHistoryService;
import com.caseflow.service.MindNodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaseHistoryServiceImpl extends ServiceImpl<CaseHistoryMapper, CaseHistory> implements CaseHistoryService {

    private final MindNodeService mindNodeService;
    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public void saveSnapshot(Long caseSetId) {
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        CaseHistory history = new CaseHistory();
        history.setCaseSetId(caseSetId);
        history.setSnapshot(objectMapper.writeValueAsString(tree));
        history.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        this.save(history);
        trimHistory(caseSetId, 10);
    }

    private void trimHistory(Long caseSetId, int maxCount) {
        List<CaseHistory> all = this.lambdaQuery()
                .eq(CaseHistory::getCaseSetId, caseSetId)
                .orderByDesc(CaseHistory::getCreatedAt)
                .list();
        if (all.size() > maxCount) {
            for (int i = maxCount; i < all.size(); i++) {
                this.removeById(all.get(i).getId());
            }
        }
    }

    @Override
    public List<CaseHistory> getRecentHistory(Long caseSetId, int limit) {
        return this.lambdaQuery()
                .eq(CaseHistory::getCaseSetId, caseSetId)
                .orderByDesc(CaseHistory::getCreatedAt)
                .last("LIMIT " + limit)
                .list();
    }

    @Override
    @SneakyThrows
    @Transactional
    public void restoreVersion(Long historyId) {
        CaseHistory history = getById(historyId);
        if (history == null) return;
        List<MindNodeDTO> tree = objectMapper.readValue(history.getSnapshot(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MindNodeDTO.class));
        mindNodeService.batchSave(history.getCaseSetId(), tree);
    }
}
