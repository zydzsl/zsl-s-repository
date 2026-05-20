package zsl.web.pojo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class emp {
    private Integer id;
    private String name;
    private String gender;
    private Integer deptId;
    private String job;
    private LocalDateTime hireTime;
    private LocalDateTime updataTime;
    private String deptname;
    private String token;

    private Integer page = 1;
    private Integer pageSize = 5;

    private List<emp_expr> emp_exprs;

}
