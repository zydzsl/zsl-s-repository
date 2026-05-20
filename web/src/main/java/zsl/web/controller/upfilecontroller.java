package zsl.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import zsl.web.pojo.Result;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@Slf4j
public class upfilecontroller {


    @PostMapping("/upload")
    public Result upload(MultipartFile file) throws Exception {
        log.info("上传文件");
        String uuid = UUID.randomUUID().toString();
        String filreType = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));

        file.transferTo(new File("D:/up/"+uuid+filreType));

        return Result.success();
    }
}
