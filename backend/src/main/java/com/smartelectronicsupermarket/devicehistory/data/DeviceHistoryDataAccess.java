package com.smartelectronicsupermarket.devicehistory.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceHistoryDataAccess {
    Optional<DeviceSnapshot> findDevice(UUID deviceId, UUID actorId);
    List<DeviceHistoryEvent> findEvents(UUID deviceId, UUID actorId, int limit, int offset);
}
