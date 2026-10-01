package io.github.surezzzzzz.sdk.mysql.route.test;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * Route MyBatis 测试 Mapper。
 *
 * @author surezzzzzz
 */
@Mapper
public interface RouteMyBatisMapper {

    /**
     * 查询当前连接的固定 database。
     *
     * @return 当前 database
     */
    @Select("SELECT DATABASE()")
    String currentDatabase();

    /**
     * 查询当前连接账号。
     *
     * @return 当前连接账号
     */
    @Select("SELECT CURRENT_USER()")
    String currentUser();
}
