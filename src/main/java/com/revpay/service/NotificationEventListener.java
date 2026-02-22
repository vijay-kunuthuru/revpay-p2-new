package com.revpay.service;

import com.revpay.model.dto.TransferCompletedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    @Autowired
    private NotificationService notificationService;

    @Async
    @EventListener
    public void handleTransferCompletedEvent(TransferCompletedEvent event) {
        notificationService.createNotification(
            event.getSenderId(), 
            "Success: INR" + event.getAmount() + " sent to " + event.getReceiverName(), 
            "TRANSFER"
        );
        notificationService.createNotification(
            event.getReceiverId(), 
            "Received: INR" + event.getAmount() + " from " + event.getSenderName(), 
            "TRANSFER"
        );
    }
}