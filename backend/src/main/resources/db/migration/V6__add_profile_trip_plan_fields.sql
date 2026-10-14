-- V6: 画像补充「具体天数」与「具体金额」
-- 原契约只有预算档位（穷游…奢华）和节奏，落到实际规划时太笼统，故补两个可填的具体数字。
-- 注意：这两列**不参与** ProfileService.missingFields() 的完整性校验——否则老用户会被判为
-- 「画像不完整」，破坏已冻结的 v2.0 契约语义。

ALTER TABLE t_user_profile
    ADD COLUMN trip_days     INTEGER,  -- 常规行程天数
    ADD COLUMN budget_amount INTEGER;  -- 人均预算（元）

COMMENT ON COLUMN t_user_profile.trip_days IS '常规行程天数（可空，不参与完整性校验）';
COMMENT ON COLUMN t_user_profile.budget_amount IS '人均预算金额，单位元（可空，不参与完整性校验）';
