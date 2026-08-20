package com.xiaozhi.dialogue.debug;

import com.xiaozhi.dialogue.DialogueService;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.stt.SttResult;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/dev")
public class DebugChatController {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DialogueService dialogueService;

    @GetMapping("/chat")
    public String testChat(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "你好，请简单介绍一下你自己。") String text) {

        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);

        if (session == null) {
            return "device session not found: " + deviceId;
        }

        if (!session.isOpen()) {
            return "device session closed: " + deviceId;
        }

        try {
            log.info(
                    "Debug CHAT start - deviceId={}, text={}",
                    deviceId,
                    text
            );

            dialogueService.handleText(
                    session,
                    SttResult.textOnly(text)
            );

            log.info(
                    "Debug CHAT handed to dialogue pipeline - deviceId={}",
                    deviceId
            );

            return "Chat triggered for device: " + deviceId;

        } catch (Exception e) {
            log.error(
                    "Debug CHAT failed - deviceId={}",
                    deviceId,
                    e
            );

            return "Chat failed: " + e.getMessage();
        }
    }
}
