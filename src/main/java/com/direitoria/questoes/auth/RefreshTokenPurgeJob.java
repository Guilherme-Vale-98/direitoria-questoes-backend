package com.direitoria.questoes.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeJob.class);

    private final RefreshTokenService refreshTokens;

    public RefreshTokenPurgeJob(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    // Daily at 03:00 server time. Deletes refresh tokens already past expiry.
    @Scheduled(cron = "0 0 3 * * *")
    public int purge() {
        int deleted = refreshTokens.purgeExpired();
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
        return deleted;
    }
}
