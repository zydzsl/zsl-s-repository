package zsl.web.service;

import zsl.web.pojo.emp;
import zsl.web.pojo.pageResult;

import java.util.List;

public interface empservice {
    pageResult PageSelect( Integer page, Integer pageSize);

    pageResult Select(emp emp);

    void save(emp emp);

    void delete(List<Integer> id);


    void updata(emp emp);

    emp select(Integer id);


    emp login(emp emp);
}
