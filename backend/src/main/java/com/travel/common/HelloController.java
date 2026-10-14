package com.travel.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping ("/api/v1")
public class HelloController {
    
    @GetMapping("/hello")
    public Result<String> hello(){
        return Result.success("后端服务连通成功，AI旅行平台后端就绪");
    }
}
