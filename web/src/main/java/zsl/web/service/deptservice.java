package zsl.web.service;

import zsl.web.pojo.Dept;

import java.util.List;

public interface deptservice {
    List<Dept> SelectAll();

    void Deleteid(Integer id);

    void adddept(Dept dept);

    void updatadept(Dept dept);

    Dept Selectid(Integer id);
}
