package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.CaseHistory;
import java.util.List;
public interface CaseHistoryService extends IService<CaseHistory> {
    void saveSnapshot(String caseSetId);
    List<CaseHistory> getRecentHistory(String caseSetId, int limit);
    void restoreVersion(String historyId);
}
