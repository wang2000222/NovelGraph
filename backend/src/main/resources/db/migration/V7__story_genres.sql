-- 平台故事题材字典；停用保留历史项目的名称快照。
CREATE TABLE ng_story_genre (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '题材主键',
 name VARCHAR(50) NOT NULL COMMENT '题材名称',
 description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '题材说明',
 sort_order INT NOT NULL DEFAULT 0 COMMENT '显示排序，数值越小越靠前',
 enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否可选：1启用，0停用',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
 PRIMARY KEY(id), UNIQUE KEY uk_story_genre_name(name)
) COMMENT='平台故事题材字典';
INSERT INTO ng_story_genre(name,description,sort_order) VALUES
('悬疑','谜案、推理与真相探寻',10),('奇幻','架空世界与超自然设定',20),('都市','现代都市生活与人物故事',30),('科幻','科学技术与未来世界',40),('历史','历史时代背景与人物故事',50),('武侠','江湖、武学与侠义',60),('仙侠','修行、仙道与东方幻想',70),('言情','情感与人物关系',80),('现实','现实生活与社会题材',90),('其他','其他类型的故事',100);
