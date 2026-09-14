package com.aegis.auth_service.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OtpService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String OTP_PREFIX = "OTP:";
    private static final String COOLDOWN_PREFIX = "OTP:COOLDOWN:";
    private static final int OTP_EXPIRATION_MINUTES = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 45;

    public void saveOtp(String email, String otp) {
        String key = OTP_PREFIX + email;
        redisTemplate.opsForValue().set(key, otp, Duration.ofMinutes(OTP_EXPIRATION_MINUTES));
    }

    public String getOtp(String email) {
        String key = OTP_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteOtp(String email) {
        String key = OTP_PREFIX + email;
        redisTemplate.delete(key);
    }

    /** Returns true if the user must wait before resending. */
    public boolean isOnCooldown(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_PREFIX + email));
    }

    /** Returns how many seconds remain in the cooldown, or 0 if not on cooldown. */
    public long getCooldownSeconds(String email) {
        Long ttl = redisTemplate.getExpire(COOLDOWN_PREFIX + email);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    /** Starts the cooldown window after an OTP is sent. */
    public void startCooldown(String email) {
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + email, "1", Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
    }
}
