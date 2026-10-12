package com.example.flow.notify;

/** Interface có hai lớp hiện thực: một lớp @Primary, một lớp chọn bằng @Qualifier. */
public interface NotificationSender {

    void send(String message);
}
