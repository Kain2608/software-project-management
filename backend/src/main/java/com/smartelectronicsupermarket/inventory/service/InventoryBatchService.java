package com.smartelectronicsupermarket.inventory.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.smartelectronicsupermarket.inventory.data.InventoryBatchDataAccess;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InventoryBatchService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final Set<String> DEVICE_FIELDS = Set.of("serial", "imei");
    private static final JsonMapper JSON_MAPPER = JsonMapper.shared();

    private final InventoryBatchDataAccess dataAccess;

    public InventoryBatchService(InventoryBatchDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public UUID[] receiveDeviceBatch(
            UUID receiptItemId,
            UUID requestKey,
            String devicesJson,
            UUID actorId) {
        requireValue(receiptItemId, "receiptItemId");
        requireValue(requestKey, "requestKey");
        requireValue(actorId, "actorId");
        validateDevicesJson(devicesJson);

        return dataAccess.receiveDeviceBatch(
                receiptItemId,
                requestKey,
                devicesJson,
                actorId);
    }

    private static void validateDevicesJson(String devicesJson) {
        if (devicesJson == null) {
            throw new IllegalArgumentException("devicesJson is required");
        }

        JsonNode devices;
        try {
            devices = JSON_MAPPER.readTree(devicesJson);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "devicesJson must contain valid JSON", exception);
        }

        if (devices == null || !devices.isArray()) {
            throw new IllegalArgumentException("devicesJson must be a JSON array");
        }
        if (devices.isEmpty() || devices.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "devicesJson must contain between 1 and 500 devices");
        }

        for (int index = 0; index < devices.size(); index++) {
            validateDevice(devices.get(index), index);
        }
    }

    private static void validateDevice(JsonNode device, int index) {
        if (!device.isObject()) {
            throw new IllegalArgumentException(
                    "devicesJson[" + index + "] must be a JSON object");
        }
        if (!DEVICE_FIELDS.containsAll(device.propertyNames())) {
            throw new IllegalArgumentException(
                    "devicesJson[" + index + "] contains an unsupported field");
        }

        JsonNode serial = device.get("serial");
        if (serial == null || !serial.isString()) {
            throw new IllegalArgumentException(
                    "devicesJson[" + index + "].serial must be a JSON string");
        }

        JsonNode imei = device.get("imei");
        if (imei != null && !imei.isString() && !imei.isNull()) {
            throw new IllegalArgumentException(
                    "devicesJson[" + index + "].imei must be a JSON string or null");
        }
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
