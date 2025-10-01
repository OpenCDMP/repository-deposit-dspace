package org.opencdmp.deposit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "org.opencdmp.deposit.*",
        "org.opencdmp.depositbase.*",
        "gr.cite.tools",
        "gr.cite.commons"
})
public class DepositApplication {

    public static void main(String[] args) {
        SpringApplication.run(DepositApplication.class, args);
    }
}
