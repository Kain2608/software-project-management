package com.smartelectronicsupermarket.serialimei.controller;

import com.smartelectronicsupermarket.serialimei.dto.SerialImeiStatusResponse;
import com.smartelectronicsupermarket.serialimei.service.SerialImeiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/serial-imei")
public class SerialImeiController {
    private final SerialImeiService serialImeiService;

    public SerialImeiController(SerialImeiService serialImeiService) {
        this.serialImeiService = serialImeiService;
    }

    @GetMapping("/status")
    public SerialImeiStatusResponse getStatus() {
        return serialImeiService.getStatus();
    }
}
