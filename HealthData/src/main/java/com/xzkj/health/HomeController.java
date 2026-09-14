package com.xzkj.health;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Paths;

@Controller
@RequestMapping("/home")
public class HomeController {

    @GetMapping(value="/list")
    public ResponseEntity<String> list() {
        try {
            // 从resources目录读取data.json文件
            Resource resource = new ClassPathResource("data.json");
            String jsonContent = new String(Files.readAllBytes(Paths.get(resource.getURI())));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonContent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"文件读取失败\"}");
        }
    }


}