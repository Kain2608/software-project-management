package com.smartelectronicsupermarket.qr.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.smartelectronicsupermarket.qr.QrPayloadParser;
import com.smartelectronicsupermarket.qr.data.QrDeviceLookupDataAccess;
import com.smartelectronicsupermarket.qr.data.QrDeviceRecord;

@Service
public class QrDeviceLookupService {

    private final QrPayloadParser payloadParser;
    private final QrDeviceLookupDataAccess dataAccess;

    public QrDeviceLookupService(
            QrPayloadParser payloadParser,
            QrDeviceLookupDataAccess dataAccess) {
        this.payloadParser = payloadParser;
        this.dataAccess = dataAccess;
    }

    public Optional<QrDeviceRecord> lookup(String rawPayload, UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }

        UUID qrToken = payloadParser.parse(rawPayload);
        return dataAccess.findByQrToken(qrToken, actorId);
    }
}
