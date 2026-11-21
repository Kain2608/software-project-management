package com.smartelectronicsupermarket.devicehistory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.devicehistory.data.*;
import org.junit.jupiter.api.Test;

class DeviceHistoryServiceTest {
    private final DeviceHistoryDataAccess data=mock(DeviceHistoryDataAccess.class);
    private final DeviceHistoryService service=new DeviceHistoryService(data);
    private final UUID device=UUID.randomUUID(),actor=UUID.randomUUID();
    @Test void returnsAuthorizedSnapshotAndPreservesPagination(){
        var snapshot=new DeviceSnapshot(device,UUID.randomUUID(),"SKU","Name",UUID.randomUUID(),"SER",null,"IN_STOCK");
        when(data.findDevice(device,actor)).thenReturn(Optional.of(snapshot)); when(data.findEvents(device,actor,20,5)).thenReturn(List.of());
        var result=service.find(device,actor,20,5); assertEquals(device,result.deviceId()); assertEquals(20,result.limit()); assertEquals(5,result.offset());
    }
    @Test void concealedDeviceIsNotFoundAndDoesNotLoadEvents(){
        when(data.findDevice(device,actor)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.find(device,actor,20,0));
        verify(data,never()).findEvents(any(),any(),anyInt(),anyInt());
    }
    @Test void rejectsInvalidPaginationAndActor(){
        assertThrows(IllegalArgumentException.class,()->service.find(device,null,20,0));
        assertThrows(IllegalArgumentException.class,()->service.find(device,actor,101,0));
        assertThrows(IllegalArgumentException.class,()->service.find(device,actor,20,-1));
        verifyNoInteractions(data);
    }
}
