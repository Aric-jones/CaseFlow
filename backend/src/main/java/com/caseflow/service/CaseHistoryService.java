package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.CaseHistory;
import java.util.List;

public interface CaseHistoryService extends IService<CaseHistory> {
    void saveSnapshot(Long caseSetId);
    List<CaseHistory> getRecentHistory(Long caseSetId, int limit);
    void restoreVersion(Long historyId);
}
