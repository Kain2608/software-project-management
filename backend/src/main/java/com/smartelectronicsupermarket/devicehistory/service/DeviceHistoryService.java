package com.smartelectronicsupermarket.devicehistory.service;

import java.util.UUID;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.devicehistory.data.DeviceHistoryDataAccess;
import com.smartelectronicsupermarket.devicehistory.dto.DeviceHistoryResponse;
import org.springframework.stereotype.Service;

@Service
public class DeviceHistoryService {
    private final DeviceHistoryDataAccess data;
    public DeviceHistoryService(DeviceHistoryDataAccess data){this.data=data;}
    public DeviceHistoryResponse find(UUID deviceId,UUID actorId,int limit,int offset){
        if(deviceId==null||actorId==null) throw new IllegalArgumentException("device and actor are required");
        if(limit<1||limit>100||offset<0) throw new IllegalArgumentException("invalid pagination");
        var device=data.findDevice(deviceId,actorId).orElseThrow(()->new ResourceNotFoundException("Device not found"));
        return DeviceHistoryResponse.from(device,data.findEvents(deviceId,actorId,limit,offset),limit,offset);
    }
}
