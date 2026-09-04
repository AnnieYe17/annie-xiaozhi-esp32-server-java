package com.xiaozhi.dialogue.debug;

import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug/servo")
public class DebugServoController {

    @Resource
    private DeviceMcpService deviceMcpService;

    @PostMapping
    public Map<String, Object> moveServo(
            @RequestBody ServoRequest request
    ) {
        return deviceMcpService.callDeviceTool(
                request.deviceId(),
                "self.servo.set_angle",
                Map.of(
                        "servo_id", request.servoId(),
                        "angle", request.angle()
                )
        );
    }

    public record ServoRequest(
            String deviceId,
            int servoId,
            int angle
    ) {
    }
}