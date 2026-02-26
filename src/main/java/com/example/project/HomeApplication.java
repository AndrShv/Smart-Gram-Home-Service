package com.example.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;



@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.project.clients")
public class HomeApplication {
        public static void main(String[] args) {
            SpringApplication.run(HomeApplication.class, args);
            //docker exec -it homeservice-mysql mysql -u root -p

        }
}
