package com.xzkj.health.service.watch;

import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.service.DeviceService;
import com.xzkj.health.service.DeviceUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchDeviceContextServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceUserService deviceUserService;

    @InjectMocks
    private WatchDeviceContextService service;

    @Test
    void resolveRegistersOnlineStatusAndLoadsCurrentBinding() {
        String imei = "123456789012345";
        Device device = new Device();
        device.setId(88L);
        DeviceUser binding = new DeviceUser();

        when(deviceService.getOrCreateByImei(imei)).thenReturn(device);
        when(deviceUserService.getCurrentBinding(88L)).thenReturn(binding);

        WatchDeviceContext context = service.resolve(imei, "76");

        assertSame(device, context.device());
        assertSame(binding, context.currentBinding());
        verify(deviceService).updateOnlineStatus(88L, "76");
    }
}
