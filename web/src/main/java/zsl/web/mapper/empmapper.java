package zsl.web.mapper;

import org.apache.ibatis.annotations.*;
import zsl.web.pojo.emp;

import java.util.List;

@Mapper
public interface empmapper {

//    @Select("select e.* , t.name deptname from employees e join tilas_user t on e.id = t.id order by update_time desc limit #{page},#{pageSize}")
//    public List<emp> PageSelect(Integer page, Integer pageSize);
//
//    @Select("select count(*) from employees")
//    public Long SelectCount();

    @Select("select e.* , t.name deptname from employees e join tilas_user t on e.id = t.id order by update_time desc")
    List<emp> PageSelect( );

    List<emp> Select(emp emp);

    @Options(useGeneratedKeys = true,keyProperty = "id")
    @Insert("insert into employees(name, gender, dept_id, job, hire_time, update_time ) values(#{name},#{gender},#{deptId},#{job},#{hireTime},#{updateTime})")
    void insert(emp emp);

    void delete(List<Integer> ids);


    void updata(emp emp);

    @Select("select * from employees where id = #{id}")
    emp getbyid(Integer id);


    emp getById(Integer id);

    @Select("select id,name from employees where name = #{name} and job = #{job}")
    emp login(emp emp);
}
