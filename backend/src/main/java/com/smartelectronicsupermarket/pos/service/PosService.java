package com.smartelectronicsupermarket.pos.service;

import com.smartelectronicsupermarket.pos.dto.ModuleStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class PosService {
    public ModuleStatusResponse getStatus() {
        return new ModuleStatusResponse(
            "POS",
            "SKELETON_READY",
            "Business workflows are intentionally not implemented in Week 2 skeleton."
        );
    }
}
