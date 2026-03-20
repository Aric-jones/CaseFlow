USE caseflow;

-- 1. api_scenario_steps: add step_type and script_content
ALTER TABLE api_scenario_steps
  ADD COLUMN step_type VARCHAR(20) DEFAULT 'API_CASE' COMMENT '步骤类型: API_CASE/SCRIPT/WAIT' AFTER scenario_id;

ALTER TABLE api_scenario_steps
  ADD COLUMN script_content TEXT NULL COMMENT 'Groovy脚本内容(step_type=SCRIPT时使用)' AFTER post_script;

-- 2. api_cases: add script_type for pre/post scripts
ALTER TABLE api_cases
  ADD COLUMN pre_script_type VARCHAR(20) DEFAULT 'JSON' COMMENT '前置脚本类型: JSON/GROOVY' AFTER post_script;

ALTER TABLE api_cases
  ADD COLUMN post_script_type VARCHAR(20) DEFAULT 'JSON' COMMENT '后置脚本类型: JSON/GROOVY' AFTER pre_script_type;

ALTER TABLE api_cases
  ADD COLUMN pre_script_content TEXT NULL COMMENT '前置Groovy脚本内容' AFTER post_script_type;

ALTER TABLE api_cases
  ADD COLUMN post_script_content TEXT NULL COMMENT '后置Groovy脚本内容' AFTER pre_script_content;

SELECT 'api_auto_enhance completed' AS result;
