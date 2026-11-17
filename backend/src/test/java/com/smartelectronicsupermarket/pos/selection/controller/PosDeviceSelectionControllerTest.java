package com.smartelectronicsupermarket.pos.selection.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.pos.selection.service.PosDeviceSelectionService;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PosDeviceSelectionControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000901");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-4000-8000-000000000902");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-4000-8000-000000000903");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-4000-8000-000000000904");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-4000-8000-000000000905");

    private PosDeviceSelectionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(PosDeviceSelectionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PosDeviceSelectionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void selectsSerialWithTrustedActorAndDoesNotAcceptActorOrPriceFromClient() throws Exception {
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, " AbC-001 ", ACTOR_ID))
                .thenReturn(DEVICE_ID);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "store_id":"00000000-0000-4000-8000-000000000902",
                                  "serial":" AbC-001 ",
                                  "actor_id":"00000000-0000-4000-8000-000000000999",
                                  "unit_price":1,
                                  "status":"SOLD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.status").value("SELECTED"));

        verify(service).lockDevice(STORE_ID, ORDER_ID, ITEM_ID, " AbC-001 ", ACTOR_ID);
    }

    @Test
    void releasesSelectionWithTrustedActorAndKeepsFalseResultExplicit() throws Exception {
        when(service.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/pos/orders/{orderId}/devices/{deviceId}", ORDER_ID, DEVICE_ID)
                        .principal(trustedActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.released").value(false));

        verify(service).releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID);
    }

    @Test
    void releasesDeviceWithTrueResultWhenOrderOwnedAndAuthorized() throws Exception {
        when(service.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/pos/orders/{orderId}/devices/{deviceId}", ORDER_ID, DEVICE_ID)
                        .principal(trustedActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.released").value(true));

        verify(service).releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID);
    }

    @Test
    void rejectsUntrustedPrincipalOnReleaseDeviceBeforeCallingService() throws Exception {
        mockMvc.perform(delete("/api/v1/pos/orders/{orderId}/devices/{deviceId}", ORDER_ID, DEVICE_ID)
                        .principal(new TestingAuthenticationToken("untrusted", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(service, never()).releaseDevice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(service, never()).lockDevice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingStoreIdInRequest() throws Exception {
        when(service.lockDevice(null, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID))
                .thenThrow(new IllegalArgumentException("storeId is required"));

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serial\":\"ABC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsBlankSerialInRequest() throws Exception {
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "   ", ACTOR_ID))
                .thenThrow(new IllegalArgumentException("serial is required"));

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void mapsDatabaseDeviceNotFoundSqlStateP0002() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "device missing",
                new SQLException("Serial ABC not found", "P0002"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString("Serial ABC not found"))));
    }

    @Test
    void mapsDatabaseValidationConstraintSqlState23514() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "validation constraint",
                new SQLException("Not available for sale", "23514"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("Not available for sale"))));
    }

    @Test
    void mapsDatabaseAccessDeniedSqlState42501() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "permission check",
                new SQLException("Permission denied", "42501"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("Permission denied"))));
    }

    @Test
    void mapsDatabaseGeneralFailureWithoutLeakingSecrets() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "internal database failure with connection string secret",
                new SQLException("connection timeout to postgres://user:secret@internal-db:5432"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("connection string"))));
    }

    @Test
    void rejectsUntrustedPrincipalBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(new TestingAuthenticationToken("client-supplied-actor", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).lockDevice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsDatabaseLockConflictWithoutLeakingSqlDetail() throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "private serial and SQL detail",
                new SQLException("device secret", "55P03"));
        when(service.lockDevice(STORE_ID, ORDER_ID, ITEM_ID, "ABC", ACTOR_ID)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/pos/orders/{orderId}/items/{itemId}/devices", ORDER_ID, ITEM_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\":\"" + STORE_ID + "\",\"serial\":\"ABC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LOCKED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString("device secret"))))
                .andExpect(content().string(not(containsString("private serial"))));
    }

    @Test
    void proposedControllerIsDisabledByDefaultAndRequiresExplicitFeatureFlag() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(PosDeviceSelectionService.class, () -> service)
                .withUserConfiguration(PosDeviceSelectionController.class);

        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(PosDeviceSelectionController.class));
        contextRunner
                .withPropertyValues("sasms.features.pos-serial-api.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(PosDeviceSelectionController.class));
    }

    private static TrustedActorAuthenticationToken trustedActor() {
        return new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID));
    }
}
