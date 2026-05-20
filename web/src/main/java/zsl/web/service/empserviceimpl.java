package zsl.web.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zsl.web.mapper.emp_exprmapper;
import zsl.web.mapper.empmapper;
import zsl.web.pojo.emp;
import zsl.web.pojo.emp_expr;
import zsl.web.pojo.pageResult;
import zsl.web.untils.JWTtools;

import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class empserviceimpl implements empservice{
    @Autowired
    private empmapper empmapper;
    @Autowired
    private emp_exprmapper emp_exprmapper;

    //分页查询
    @Override
    public pageResult PageSelect(Integer page, Integer pageSize) {
        PageHelper.startPage(page,pageSize);
//        Integer start = (page-1)*pageSize;
        List<emp> emps = empmapper.PageSelect();
        Page<emp> p = (Page<emp>) emps;

        return new pageResult<emp>(p.getTotal(),p.getResult());
    }

    //按条件分页查询
    @Override
    public pageResult Select(emp emp) {
        emp.setUpdataTime(LocalDateTime.now());

        PageHelper.startPage(emp.getPage(),emp.getPageSize());
        List<emp> emps = empmapper.Select(emp);
        Page<emp> p = (Page<emp>) emps;
        return new pageResult<emp>(p.getTotal(),p.getResult());
    }
//propagation = Propagation.REQUIRES_NEW的意思是，如果方法A调用方法B，方法B的事务是独立存在的，方法A，方法B的不会相互影响。
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)//控制事务，整个方法都执行成功才会实现功能，四个属性，原子性，一致性，隔离性，持久性。
    @Override
    public void save(emp emp) {
        emp.setUpdataTime(LocalDateTime.now());
        emp.setHireTime(LocalDateTime.now());
        empmapper.insert(emp);
        List<emp_expr> emp_exprs = emp.getEmp_exprs();
        if(!emp_exprs.isEmpty()){
            emp_exprs.forEach(emp_expr -> {
                emp_expr.setEmpId(emp.getId());
            });
            emp_exprmapper.insertbatch(emp.getEmp_exprs());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(List<Integer> id) {

        empmapper.delete(id);

        emp_exprmapper.delete(id);
    }


    @Override
    public emp select(Integer id) {

//        emp emp = empmapper.getbyid(id);
//        List<emp_expr> emp_expr = emp_exprmapper.select(id);
//
//        emp.setEmp_exprs(emp_expr);
        emp emp = empmapper.getById(id);
        emp.setUpdataTime(LocalDateTime.now());
      return emp;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updata(emp emp) {
        emp.setUpdataTime(LocalDateTime.now());
        List<emp_expr> emp_exprs = emp.getEmp_exprs();

        empmapper.updata(emp);
        emp_exprmapper.delete(Arrays.asList(emp.getId()));

        if(!emp_exprs.isEmpty()){
            emp_exprs.forEach(emp_expr -> {
                emp_expr.setEmpId(emp.getId());
            });
            emp_exprmapper.insertbatch(emp_exprs);
        }
    }

    @Override
    public emp login(emp emp) {

        emp e = empmapper.login(emp);
        if (e.getName() != null) {
            e.setToken(JWTtools.createJWT(e.getId().toString(), e.getName()));
            log.info("员工登录成功");
        }
        return e;
    }

}
