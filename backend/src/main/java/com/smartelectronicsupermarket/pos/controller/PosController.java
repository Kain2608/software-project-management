package com.smartelectronicsupermarket.pos.controller;

import com.smartelectronicsupermarket.pos.dto.ModuleStatusResponse;
import com.smartelectronicsupermarket.pos.service.PosService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos")
public class PosController {
    private final PosService posService;

    public PosController(PosService posService) {
        this.posService = posService;
    }

    @GetMapping("/status")
    public ModuleStatusResponse getStatus() {
        return posService.getStatus();
    }
}
