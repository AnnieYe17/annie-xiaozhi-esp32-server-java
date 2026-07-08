package com.xiaozhi.ai.tts.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.ai.utils.HttpUtil;

import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VolcengineTtsService implements TtsService {
    private static final String PROVIDER_NAME = "volcengine";
    private static final String API_URL = "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    private static final String RESOURCE_ID = "seed-tts-2.0";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // 音频输出路径
    private String outputPath;

    // API相关
    private String accessToken; // 对应 apiKey

    // 语音参数（voiceName, pitch, speed）
    private final XiaozhiTtsOptions options;

    private final OkHttpClient client = HttpUtil.client;

    public VolcengineTtsService(ConfigBO config, String voiceName, Double pitch, Double speed, String outputPath) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
        this.accessToken = config.getApiKey();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public XiaozhiTtsOptions getOptions() {
        return options;
    }

    @Override
    public Path textToSpeech(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            log.warn("文本内容为空！");
            return null;
        }

        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                // 生成音频文件名
                String audioFileName = getAudioFileName();
                String audioFilePath = outputPath + audioFileName;

                // 发送POST请求
                boolean success = sendRequest(text, audioFilePath);

                if (success) {
                    return Path.of(audioFilePath);
                } else {
                    throw new Exception("语音合成失败");
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("火山语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    log.error("火山语音合成失败，已达到最大重试次数", e);
                    throw e;
                }
            }
        }
        throw new Exception("语音合成失败");
    }

    /**
     * 发送POST请求到火山引擎API，获取语音合成结果
     */
    private boolean sendRequest(String text, String audioFilePath) throws Exception {
    try {
        JsonObject requestJson = new JsonObject();

        JsonObject user = new JsonObject();
        user.addProperty("uid", UUID.randomUUID().toString());
        requestJson.add("user", user);

        JsonObject audioParams = new JsonObject();
        audioParams.addProperty("format", "pcm");
        audioParams.addProperty("sample_rate", 24000);
        audioParams.addProperty("enable_timestamp", true);

        JsonObject reqParams = new JsonObject();
        reqParams.addProperty("text", text);
        reqParams.addProperty("speaker", getVoiceName());
        reqParams.add("audio_params", audioParams);

        requestJson.add("req_params", reqParams);
        log.info("Volcengine TTS request body = {}", requestJson);
        log.info("Volcengine RESOURCE_ID = {}, voiceName = {}", RESOURCE_ID, getVoiceName());
        
        RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Api-Key", accessToken)
                .addHeader("X-Api-Resource-Id", RESOURCE_ID)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("TTS请求失败: {} {}, 错误信息: {}, 原始内容: {}",
                        response.code(), response.message(), errorBody, text);
                return false;
            }

            if (response.body() == null) {
                log.error("TTS响应体为空");
                return false;
            }

            byte[] buffer;

            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(response.body().charStream());
                 java.io.ByteArrayOutputStream audioBytes =
                         new java.io.ByteArrayOutputStream()) {

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    JsonObject jsonResponse = JsonParser.parseString(line).getAsJsonObject();
                    int code = jsonResponse.has("code") ? jsonResponse.get("code").getAsInt() : -1;

                    if (code == 0 && jsonResponse.has("data") && !jsonResponse.get("data").isJsonNull()) {
                        String base64Audio = jsonResponse.get("data").getAsString();
                        byte[] chunk = Base64.getDecoder().decode(base64Audio);
                        audioBytes.write(chunk);
                        continue;
                    }

                    if (code == 0 && jsonResponse.has("sentence")) {
                        log.debug("TTS sentence data: {}", jsonResponse);
                        continue;
                    }

                    if (code == 20000000) {
                        break;
                    }

                    if (code > 0) {
                        log.error("TTS返回错误: {}", jsonResponse);
                        return false;
                    }
                }

                buffer = audioBytes.toByteArray();
            }

            if (buffer.length == 0) {
                log.error("TTS没有返回音频数据");
                return false;
            }

            File audioFile = new File(audioFilePath);
            try (FileOutputStream fout = new FileOutputStream(audioFile)) {
                fout.write(buffer);
            }

            return true;
        }
    } catch (Exception e) {
        log.error("发送TTS请求时发生错误", e);
        throw new Exception("发送TTS请求失败", e);
    }
}
}
