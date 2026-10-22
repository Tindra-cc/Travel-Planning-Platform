-- V24: 会话保留来源推荐。从推荐攻略进入对话时，模型在每一轮都能看到这份推荐的完整内容。
ALTER TABLE t_chat_session ADD COLUMN reference_post_id BIGINT;
