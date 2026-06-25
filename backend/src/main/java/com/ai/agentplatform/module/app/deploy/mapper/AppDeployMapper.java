package com.ai.agentplatform.module.app.deploy.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppDeployMapper {

    @Select("""
            SELECT id, app_name, app_code, deploy_url, cover_img
            FROM app
            WHERE id = #{appId}
            """)
    AppDeployRecord selectDeployInfo(@Param("appId") Long appId);

    @Update("UPDATE app SET deploy_url = #{deployUrl} WHERE id = #{appId}")
    int updateDeployUrl(@Param("appId") Long appId, @Param("deployUrl") String deployUrl);

    @Update("UPDATE app SET cover_img = #{coverImg} WHERE id = #{appId}")
    int updateCoverImg(@Param("appId") Long appId, @Param("coverImg") String coverImg);

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = #{tableName}
              AND COLUMN_NAME = #{columnName}
            """)
    Integer countColumn(@Param("tableName") String tableName, @Param("columnName") String columnName);

    @Update("ALTER TABLE `${tableName}` ADD COLUMN `${columnName}` ${definition}")
    void addColumn(
            @Param("tableName") String tableName,
            @Param("columnName") String columnName,
            @Param("definition") String definition);

    @Select("SELECT COUNT(*) FROM app WHERE id = 1")
    Integer countMockApp();

    @Insert("""
            INSERT INTO app (id, user_id, app_name, description, app_code, is_featured, status)
            VALUES (1, 1, 'Mock 测试应用', '并行开发期默认应用，供预览/对话/生成联调',
                    '<html><body><h1>Hello Mock App</h1></body></html>', 0, 'normal')
            """)
    void insertMockApp();
}
