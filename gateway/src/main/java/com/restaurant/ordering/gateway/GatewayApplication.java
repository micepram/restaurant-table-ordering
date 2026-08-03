package com.restaurant.ordering.gateway;

import com.restaurant.ordering.gateway.auth.StaffProperties;
import com.restaurant.ordering.gateway.config.ServiceRoutes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StaffProperties.class, ServiceRoutes.GatewayServiceProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
