package com.xzkj.health.service.watch;

import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.service.DeviceService;
import com.xzkj.health.service.DeviceUserService;
import org.springframework.stereotype.Service;

@Service
public class WatchDeviceContextService {

    private final DeviceService deviceService;
    private final DeviceUserService deviceUserService;

    public WatchDeviceContextService(DeviceService deviceService,
                                     DeviceUserService deviceUserService) {
        this.deviceService = deviceService;
        this.deviceUserService = deviceUserService;
    }

    public WatchDeviceContext resolve(String imei, String battery) {
        Device device = deviceService.getOrCreateByImei(imei);
        deviceService.updateOnlineStatus(device.getId(), battery);
        DeviceUser currentBinding = deviceUserService.getCurrentBinding(device.getId());
        return new WatchDeviceContext(device, currentBinding);
    }
}
