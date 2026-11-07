package com.smartelectronicsupermarket.serialimei.service;

import com.smartelectronicsupermarket.serialimei.dto.SerialImeiStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class SerialImeiService {
    public SerialImeiStatusResponse getStatus() {
        return new SerialImeiStatusResponse(
            "Serial/IMEI",
            "SKELETON_READY",
            "Validation, persistence and lifecycle rules require approved business requirements."
        );
    }
}
