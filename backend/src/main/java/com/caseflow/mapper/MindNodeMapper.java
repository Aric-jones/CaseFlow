package com.caseflow.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.caseflow.entity.MindNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface MindNodeMapper extends BaseMapper<MindNode> {

    @Insert("<script>" +
            "INSERT INTO mind_nodes (id, case_set_id, parent_id, text, node_type, sort_order, is_root, properties, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='n' separator=','>" +
            "(#{n.id}, #{n.caseSetId}, #{n.parentId}, #{n.text}, #{n.nodeType}, #{n.sortOrder}, #{n.isRoot}, " +
            "#{n.properties, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, NOW(), NOW())" +
            "</foreach>" +
            " ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), text=VALUES(text), node_type=VALUES(node_type), " +
            "sort_order=VALUES(sort_order), is_root=VALUES(is_root), properties=VALUES(properties), updated_at=NOW()" +
            "</script>")
    int batchUpsert(@Param("list") List<MindNode> nodes);
}
