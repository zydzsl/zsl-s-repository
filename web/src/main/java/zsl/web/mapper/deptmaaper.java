package zsl.web.mapper;

import org.apache.ibatis.annotations.*;
import zsl.web.pojo.Dept;

import java.util.List;

@Mapper
public interface deptmaaper {
    @Select("select id,name,create_time,update_time from tilas_user order by update_time desc")
    List<Dept> findAll();

    @Delete("delete from tilas_user where id=#{id}")
    void Deleteid(Integer id);

    @Insert("insert into tilas_user(name,create_time,update_time) values(#{name},#{createTime},#{updateTime} )")
    void adddept(Dept dept);

    @Update("update tilas_user set name=#{name},update_time=#{updateTime} where id=#{id}")
    void updatadept(Dept dept);

    @Select("select id,name,create_time,update_time from tilas_user where id=#{id} ")
    Dept Selectid(Integer id);
}
