package zsl.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import zsl.web.pojo.Result;
import zsl.web.pojo.emp;
import zsl.web.pojo.pageResult;
import zsl.web.service.empservice;

import java.util.List;

@RestController
@Slf4j
public class empcontroller {

    @Autowired
    private empservice empservice;

//    @GetMapping("/emps")
//    public Result PageSelect(Integer page,Integer pageSize){
//        log.info("查询了员工数据");
//        pageResult p =  empservice.PageSelect(page, pageSize);
//        return Result.success(p);
//    }

    @GetMapping("/emps")
    public Result PageSelect(emp emp){
        log.info("按条件查询了员工数据");
        pageResult<emp> p =  empservice.Select(emp);
        return Result.success(p);
    }
    @PostMapping("/emps")
    public Result save(@RequestBody emp emp){
        log.info("成功添加了员工数据");
        empservice.save(emp);
        return Result.success();
    }
    //可以直接由数组接受，用集合接受必须添加@RequestParam
    @DeleteMapping("/emps")
    public Result delete(@RequestParam List<Integer> id){
        log.info("成功删除了员工数据");
        empservice.delete(id);
        return Result.success();
    }

    //查询某个员工,路径参数需要添加@PathVariable，否则无法获取，请求参数添加@RequestParam，可省略，但必须参数名一样
    @GetMapping("/emps/{id}")
    public Result Selectid(@PathVariable("id") Integer id){
        log.info("查询了id为{}的部门数据",id);//占位符组合字符串
        emp emp = empservice.select(id);
        return Result.success(emp);
    }

    //修改员工
    @PutMapping("/emps")
    public Result updata(@RequestBody emp emp){
        log.info("成功修改员工数据");
        empservice.updata(emp);
        return Result.success();
    }

    @PostMapping("/emps/login")
    public Result login(@RequestBody emp emp){
        log.info("员工登录");
        emp e = empservice.login(emp);
        if(e.getName()==null)
            return Result.error("没有这个账户");
        return Result.success(e);
    }
    //员工岗位统计人数


}
