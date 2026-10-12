package com.example.flow.notify;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component("email")
public class EmailSender implements NotificationSender {

    @Override
    public void send(String message) {}
}
