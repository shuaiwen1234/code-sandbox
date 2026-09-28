package com.wen.codesandbox.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wen.codesandbox.model.ExecuteCodeRequest;
import com.wen.codesandbox.model.ExecuteCodeResponse;
import com.wen.codesandbox.newJavaDockerCodeSandBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
public class MainController {

    private final String SECRET_KEY = "mySecretKey";
    private final String SALT = "zhangziwenzuishaui";
    private final String AUTH_HEADER = "apiAuth";
    private final String TIME_STAMP_HEADER = "timeStamp";
    private final String RANDOM_HEADER = "random";

    // 推荐显式声明泛型
    private final Cache cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    @Autowired
    private newJavaDockerCodeSandBox javaDockerCodeSandBox;

    @GetMapping("/health")
    public String health() {
        return "Hello World!";
    }

    /**
     * 执行代码
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody(required = true) ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        String timeStamp = request.getHeader(TIME_STAMP_HEADER);
        String random = request.getHeader(RANDOM_HEADER);
        String authHeader = request.getHeader(AUTH_HEADER);

        // 1. 请求头全量判空
        if (StrUtil.hasEmpty(timeStamp, random, authHeader)) {
            response.setStatus(403);
            return null;
        }

        // 2. 时间戳范围校验（防非法字符串 + 防未来时间戳，偏差大于 5 分钟直接拒绝）
        try {
            long clientTime = Long.parseLong(timeStamp);
            if (Math.abs(System.currentTimeMillis() - clientTime) > 5 * 60 * 1000) {
                response.setStatus(403);
                return null;
            }
        } catch (NumberFormatException e) {
            response.setStatus(403);
            return null;
        }

        // 3. 重放攻击校验
        String cacheKey = timeStamp + "_" + random;
        if (cache.getIfPresent(cacheKey) != null) {
            response.setStatus(403);
            return null;
        }

        // 4. 签名合法性校验
        String key = SECRET_KEY + SALT;
        String str = key + timeStamp + random;
        String accessKey = DigestUtil.md5Hex(str);
        if (!accessKey.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }

        // 5. 校验全部通过后再写入缓存
        cache.put(cacheKey, 1);

        return javaDockerCodeSandBox.executeCode(executeCodeRequest);
    }
}