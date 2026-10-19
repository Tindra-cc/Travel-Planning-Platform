-- V11: 每日生成配额的日期标记。跨天时由生成服务在行锁内把 used 重置为 0。
ALTER TABLE t_user ADD COLUMN generate_quota_date DATE;
