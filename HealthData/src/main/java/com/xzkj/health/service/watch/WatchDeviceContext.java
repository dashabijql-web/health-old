package com.xzkj.health.service.watch;

import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;

public record WatchDeviceContext(Device device, DeviceUser currentBinding) {
}
