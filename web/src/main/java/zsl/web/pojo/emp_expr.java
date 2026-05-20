package zsl.web.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class emp_expr {
    private Integer id;
    private Integer empId;
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
    private String job;
    private String company;
}
