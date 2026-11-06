package com.stockmanagement.user.application;

import com.stockmanagement.user.application.port.UserStore;
import com.stockmanagement.user.domain.DisplayName;
import com.stockmanagement.user.domain.KakaoId;
import com.stockmanagement.user.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 로그인 시 가입·로그인 처리(research R2·R6). 처음 보는 카카오 사용자는 먼저 레거시 사용자(기능 001 데이터)를
 * 차지해 보고, 없으면 새 사용자로 가입한다.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserStore users;
    private final Clock clock;

    public UserService(UserStore users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public UserId signIn(KakaoId kakaoId, DisplayName displayName) {
        Instant now = clock.instant();
        Optional<UserId> existing = users.findByKakaoId(kakaoId).map(user -> user.id());
        if (existing.isPresent()) {
            users.touchLogin(existing.get(), displayName, now);
            return existing.get();
        }
        Optional<UserId> claimed = users.claimLegacy(kakaoId, displayName, now);
        if (claimed.isPresent()) {
            log.info("레거시 데이터 인수: kakaoId={}, userId={}", kakaoId, claimed.get());
            return claimed.get();
        }
        UserId created = users.insert(kakaoId, displayName, now);
        log.info("새 사용자 가입: userId={}", created);
        return created;
    }
}
