package com.example.flow.order;

import com.example.flow.common.AuditService;
import com.example.flow.common.BusinessException;
import com.example.flow.notify.NotificationSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final NotificationSender notificationSender;
    private final NotificationSender smsSender;
    private final AuditService auditService;
    private final InventoryClient inventoryClient;
    private final PaymentGateway paymentGateway;
    private final CatalogGateway catalogGateway;

    public OrderService(
            AuditService auditService,
            OrderRepository orderRepository,
            NotificationSender notificationSender,
            @Qualifier("sms") NotificationSender smsSender,
            InventoryClient inventoryClient,
            PaymentGateway paymentGateway,
            CatalogGateway catalogGateway) {
        this.auditService = auditService;
        this.orderRepository = orderRepository;
        this.notificationSender = notificationSender;
        this.smsSender = smsSender;
        this.inventoryClient = inventoryClient;
        this.paymentGateway = paymentGateway;
        this.catalogGateway = catalogGateway;
    }

    public void confirm(Long id) {
        var order = orderRepository.findById(id).orElseThrow();
        if (!order.isPending()) {
            throw new BusinessException("ORDER_NOT_PENDING");
        }
        catalogGateway.productOptions();
        inventoryClient.reserve(id);
        paymentGateway.charge(id);
        notificationSender.send("Đơn hàng đã xác nhận");
        smsSender.send("Đơn hàng đã xác nhận");
        auditService.record(id);
    }
}
