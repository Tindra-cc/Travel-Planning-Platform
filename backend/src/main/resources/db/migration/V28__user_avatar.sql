-- 个人中心头像：存预置 emoji 或图片 URL，为空时前端回退到昵称首字。
ALTER TABLE t_user ADD COLUMN avatar VARCHAR(512);
