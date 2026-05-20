package zsl.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import zsl.web.pojo.Dept;
import zsl.web.pojo.Result;
import zsl.web.service.deptservice;

import java.util.List;


//@Controller
//@ResponeBody("/dept") 可以把方法的返回值直接作为响应数据返回给页面，返回值是对象或者集合会解析成json格式，页面会显示这个返回值

//@RequestMapping("/dept")  //可以添加方法里共同的路径
@Slf4j
@RestController
public class deptcontroller {

    @Autowired
    private deptservice deptservice;



//  @RequestMapping(value = "/depts",method = RequestMethod.GET)
    @GetMapping("/depts")
    public Result SelectAll(){
//         System.out.println("查询了全部部门数据");
         log.info("查询了全部部门数据");
         List<Dept> deptlist = deptservice.SelectAll();
         return Result.success(deptlist);
    }

    //根据id查询部门数据,请求参数名和方法参数一致，可以省略 ("id")
    @GetMapping("/depts/{id}")
    public Result Selectid(@PathVariable("id") Integer id){
//        System.out.println("查询了id为"+id+"的部门数据");
        log.info("查询了id为{}的部门数据",id);//占位符组合字符串
        Dept dept = deptservice.Selectid(id);
        return Result.success(dept);
    }

//    @DeleteMapping("/depts")
//    public Result deleteid(HttpServletRequest  request){
//        System.out.println("成功删除");
//request.getParameter("id")的返回值的类型是String，所以必需转换
//        Integer id = Integer.parseInt(request.getParameter("id"));
//        deptservice.Deleteid(id);
//        return Result.success();
//    }

    //方式二 一旦声明了请求参数，那么必须输入参数，不想的话就必须加required = false
//    @DeleteMapping("/depts")
//    public Result deleteid(@RequestParam("id") Integer id){
//        System.out.println("成功删除");
//        deptservice.Deleteid(id);
//        return Result.success();
//    }

    //方式 三 请求参数名和方法参数一致，可以省略@RequestParam、
    // Get请求参数可直接获取
    @DeleteMapping("/depts")
    public Result deleteid(Integer id){
//        System.out.println("成功删除");
        log.info("成功删除");
        deptservice.Deleteid(id);
        return Result.success();
    }

    //Post方法的json格式的请求通常使用对象来接受参数
    @PostMapping("/depts")
    public Result adddept(@RequestBody Dept dept){
//        System.out.println("成功添加");
        log.info("成功添加");
        deptservice.adddept(dept);
        return Result.success();
    }

    @PutMapping("/depts")
    public Result updata(@RequestBody Dept dept){
//        System.out.println("成功修改");
        log.info("成功修改");
        deptservice.updatadept(dept);
        return Result.success();
    }

}
