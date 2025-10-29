package az.kapital.mspaymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "az.kapital.mspaymentservice.client")
public class MsPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsPaymentServiceApplication.class, args);
    }

}
