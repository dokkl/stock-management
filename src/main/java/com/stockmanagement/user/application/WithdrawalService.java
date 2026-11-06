package com.stockmanagement.user.application;

import com.stockmanagement.common.error.ResourceNotFoundException;
import com.stockmanagement.user.application.port.KakaoUnlinker;
import com.stockmanagement.user.application.port.UnlinkQueue;
import com.stockmanagement.user.application.port.UserStore;
import com.stockmanagement.user.domain.AppUser;
import com.stockmanagement.user.domain.UserId;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴(FR-015, research R7). 먼저 사용자 데이터를 연쇄 삭제해 커밋하고, 그다음 카카오 연결을 해제한다. 해제에
 * 실패해도 탈퇴는 끝난 것이고, 해제는 재시도 대기열에서 다시 한다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final UserStore users;
    private final KakaoUnlinker unlinker;
    private final UnlinkQueue queue;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WithdrawalService(
            UserStore users, KakaoUnlinker unlinker, UnlinkQueue queue, TransactionTemplate transactions, Clock clock) {
        this.users = users;
        this.unlinker = unlinker;
        this.queue = queue;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void withdraw(UserId userId) {
        AppUser user = users.find(userId).orElseThrow(() -> new ResourceNotFoundException("user", userId.value()));
        transactions.executeWithoutResult(status -> users.delete(userId));
        log.info("회원 탈퇴: userId={}", userId);
        if (user.kakaoId() == null) {
            return;
        }
        UnlinkResult result = unlinker.unlink(user.kakaoId());
        if (!result.unlinked()) {
            queue.enqueue(user.kakaoId(), result.reason(), UnlinkRetryScheduler.nextAttempt(clock.instant(), 1));
        }
    }
}
