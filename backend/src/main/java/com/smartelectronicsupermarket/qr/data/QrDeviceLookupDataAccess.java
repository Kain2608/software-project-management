package com.smartelectronicsupermarket.qr.data;

import java.util.Optional;
import java.util.UUID;

public interface QrDeviceLookupDataAccess {

    Optional<QrDeviceRecord> findByQrToken(UUID qrToken, UUID actorId);
}
