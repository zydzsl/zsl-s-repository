package zsl.web.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import zsl.web.pojo.emp;
import zsl.web.pojo.emp_expr;

import java.util.List;

@Mapper
public interface emp_exprmapper {


    void insertbatch(List<emp_expr> emp);

    void delete(List<Integer> ids);

    @Select("select * from emp_expr where emp_id = #{id}")
    List<emp_expr> select(Integer  id);


}
