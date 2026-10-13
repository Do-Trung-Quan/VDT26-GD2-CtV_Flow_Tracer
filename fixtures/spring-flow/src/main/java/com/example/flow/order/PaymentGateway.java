package com.example.flow.order;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Thu tiền qua cổng thanh toán bên ngoài bằng RestTemplate (URL tuyệt đối ghép từ hằng số). */
@Component
public class PaymentGateway {

    private static final String PAYMENT_URL = "https://payment.example.com/api";

    private final RestTemplate restTemplate;

    public PaymentGateway(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void charge(Long orderId) {
        restTemplate.postForObject(PAYMENT_URL + "/charges", orderId, Void.class);
    }
}
