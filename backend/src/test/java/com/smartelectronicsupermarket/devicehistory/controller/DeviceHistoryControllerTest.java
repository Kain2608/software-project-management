package com.smartelectronicsupermarket.devicehistory.controller;

import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;import com.smartelectronicsupermarket.devicehistory.dto.DeviceHistoryResponse;import com.smartelectronicsupermarket.devicehistory.service.DeviceHistoryService;import com.smartelectronicsupermarket.security.identity.*;import org.junit.jupiter.api.*;import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class DeviceHistoryControllerTest{
 private final UUID device=UUID.randomUUID(),actor=UUID.randomUUID();private DeviceHistoryService service;private MockMvc mvc;
 @BeforeEach void setup(){service=mock(DeviceHistoryService.class);mvc=MockMvcBuilders.standaloneSetup(new DeviceHistoryController(service)).setControllerAdvice(new ApiExceptionHandler()).build();}
 @Test void trustedActorIsTakenFromPrincipal()throws Exception{var response=new DeviceHistoryResponse(device,UUID.randomUUID(),"SKU","Name",UUID.randomUUID(),"SER",null,"IN_STOCK",List.of(),20,0);when(service.find(device,actor,20,0)).thenReturn(response);mvc.perform(get("/api/v1/devices/{id}/history",device).param("limit","20").principal(new TrustedActorAuthenticationToken(new AuthenticatedActor(actor)))).andExpect(status().isOk()).andExpect(jsonPath("$.device_id").value(device.toString()));verify(service).find(device,actor,20,0);}
 @Test void missingTrustedPrincipalIsForbiddenBeforeService()throws Exception{mvc.perform(get("/api/v1/devices/{id}/history",device)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));verifyNoInteractions(service);}
}
