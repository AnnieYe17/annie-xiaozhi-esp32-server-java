package com.xiaozhi.dev;

import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class DevController {

    @Resource
    private ConfigService configService;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @GetMapping("/dev/tts")
    public String testTts() throws Exception {
        ConfigBO config = configService.getBO(2);

        Path path = ttsServiceFactory
                .getTtsService(
                        config,
                        "zh_female_shuangkuaisisi_moon_bigtts",
                        1.0,
                        1.0
                )
                .textToSpeech("你好，我是小智。");

        return path != null ? path.toString() : "TTS failed: path is null";
    }
}
