package com.caseflow.service.impl;

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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaseHistoryServiceImpl extends ServiceImpl<CaseHistoryMapper, CaseHistory> implements CaseHistoryService {
    private final MindNodeService mindNodeService;
    private final ObjectMapper objectMapper;
    private static final int INTERVAL_MINUTES = 15;
    private static final int MAX_VERSIONS = 20;

    @Override @SneakyThrows
    public void saveSnapshot(String caseSetId) {
        CaseHistory latest = lambdaQuery().eq(CaseHistory::getCaseSetId, caseSetId).orderByDesc(CaseHistory::getCreatedAt).last("LIMIT 1").one();
        if (latest != null && latest.getCreatedAt().plusMinutes(INTERVAL_MINUTES).isAfter(LocalDateTime.now())) return;
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        CaseHistory h = new CaseHistory(); h.setCaseSetId(caseSetId);
        h.setSnapshot(objectMapper.writeValueAsString(tree)); h.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        save(h); trimHistory(caseSetId);
    }
    private void trimHistory(String caseSetId) {
        List<CaseHistory> all = lambdaQuery().eq(CaseHistory::getCaseSetId, caseSetId).orderByDesc(CaseHistory::getCreatedAt).list();
        if (all.size() > MAX_VERSIONS) for (int i = MAX_VERSIONS; i < all.size(); i++) removeById(all.get(i).getId());
    }
    @Override
    public List<CaseHistory> getRecentHistory(String caseSetId, int limit) {
        return lambdaQuery().eq(CaseHistory::getCaseSetId, caseSetId).orderByDesc(CaseHistory::getCreatedAt).last("LIMIT " + limit).list();
    }
    @Override @SneakyThrows @Transactional
    public void restoreVersion(String historyId) {
        CaseHistory h = getById(historyId); if (h == null) return;
        List<MindNodeDTO> tree = objectMapper.readValue(h.getSnapshot(), objectMapper.getTypeFactory().constructCollectionType(List.class, MindNodeDTO.class));
        mindNodeService.batchSave(h.getCaseSetId(), tree);
    }
}
