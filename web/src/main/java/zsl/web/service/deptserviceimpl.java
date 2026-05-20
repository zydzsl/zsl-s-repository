package zsl.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zsl.web.mapper.deptmaaper;
import zsl.web.pojo.Dept;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
public class deptserviceimpl implements deptservice{
    
    @Autowired
    private deptmaaper deptmaaper;
    @Override
    public List<Dept> SelectAll() {
        return deptmaaper.findAll();
    }

    @Override
    public void Deleteid(Integer  id) {
        deptmaaper.Deleteid(id);
    }


    @Override
    public void adddept(Dept dept) {
        //获取当前系统时间
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());
        deptmaaper.adddept(dept);
    }

    @Override
    public void updatadept(Dept dept) {
        dept.setUpdateTime(LocalDateTime.now());
        deptmaaper.updatadept(dept);
    }
    @Override
    public Dept Selectid (Integer id) {
        return deptmaaper.Selectid(id);
    }
}
