package com.xiaozhi.dialogue.debug;

import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/dev")
public class DebugTtsController {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private MessageSender messageService;

    @GetMapping("/tts")
    public String testDeviceTts(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "你好，这是扬声器测试。") String text) {

        ChatSession chatSession = sessionManager.getSessionByDeviceId(deviceId);

        if (chatSession == null) {
            return "device session not found: " + deviceId;
        }

        if (!chatSession.isOpen()) {
            return "device session closed: " + deviceId;
        }

        Thread.startVirtualThread(() -> {
            try {
                log.info("Debug TTS start - deviceId={}, text={}", deviceId, text);

                Path audioPath = ttsFactory
                        .getDefaultTtsService()
                        .textToSpeech(text);

                if (audioPath == null) {
                    log.error("Debug TTS failed: audioPath is null");
                    return;
                }

                log.info("Debug TTS generated - path={}", audioPath);

                Player player = new ScheduledPlayer(
                        chatSession,
                        messageService
                );

                player.play(text, audioPath);

                log.info("Debug TTS handed to player - deviceId={}", deviceId);

            } catch (Exception e) {
                log.error("Debug TTS failed - deviceId={}", deviceId, e);
            }
        });

        return "TTS triggered for device: " + deviceId;
    }
}
