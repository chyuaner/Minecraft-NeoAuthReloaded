package tw.yuaner.neoauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.yuaner.neoauth.util.MojangCircuitBreaker;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MojangCircuitBreakerTest {

    @BeforeEach
    public void setUp() {
        MojangCircuitBreaker.reset();
    }

    @Test
    public void testInitialStateNotTripped() {
        assertFalse(MojangCircuitBreaker.isTripped());
        assertEquals(0, MojangCircuitBreaker.getCooldownSecondsRemaining());
    }

    @Test
    public void testRecordFailureTripsCircuitBreaker() {
        MojangCircuitBreaker.recordFailure("測試異常");
        assertTrue(MojangCircuitBreaker.isTripped());
        assertTrue(MojangCircuitBreaker.getCooldownSecondsRemaining() > 0);
        assertEquals("測試異常", MojangCircuitBreaker.getLastFailureReason());
    }

    @Test
    public void testTripManually() {
        MojangCircuitBreaker.tripManually(60, "管理員手動測試");
        assertTrue(MojangCircuitBreaker.isTripped());
        assertTrue(MojangCircuitBreaker.getCooldownSecondsRemaining() > 0);
        assertTrue(MojangCircuitBreaker.getCooldownSecondsRemaining() <= 60);
        assertEquals("管理員手動測試", MojangCircuitBreaker.getLastFailureReason());
    }

    @Test
    public void testRecordSuccessResetsTrippedBreaker() {
        MojangCircuitBreaker.tripManually(100, "測試");
        assertTrue(MojangCircuitBreaker.isTripped());

        MojangCircuitBreaker.recordSuccess();
        assertFalse(MojangCircuitBreaker.isTripped());
        assertEquals(0, MojangCircuitBreaker.getCooldownSecondsRemaining());
    }

    @Test
    public void testClientDisconnectionThreshold() {
        assertFalse(MojangCircuitBreaker.isTripped());

        // 第 1 次斷線，尚未達標
        MojangCircuitBreaker.recordClientDisconnection("player1");
        assertFalse(MojangCircuitBreaker.isTripped());

        // 第 2 次斷線，達標觸發熔斷
        MojangCircuitBreaker.recordClientDisconnection("player2");
        assertTrue(MojangCircuitBreaker.isTripped());
        assertTrue(MojangCircuitBreaker.getLastFailureReason().contains("連續 2 次客戶端"));
    }

    @Test
    public void testAuthManagerFallbackMarking() {
        UUID testUuid = UUID.randomUUID();
        assertFalse(AuthManager.isMojangFallback(testUuid));

        AuthManager.markMojangFallback(testUuid);
        assertTrue(AuthManager.isMojangFallback(testUuid));

        AuthManager.clearMojangFallback(testUuid);
        assertFalse(AuthManager.isMojangFallback(testUuid));
    }
}
