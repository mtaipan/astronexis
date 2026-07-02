package dev.taipan.server_site.service;

import dev.taipan.server_site.config.RconProperties;
import dev.taipan.server_site.model.GrantStatus;
import dev.taipan.server_site.model.Platform;
import dev.taipan.server_site.model.VipGrant;
import dev.taipan.server_site.rcon.RconClient;
import dev.taipan.server_site.rcon.RconException;
import dev.taipan.server_site.repository.VipGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты доставки VIP-грантов (SEC-3), в первую очередь — защита от двойной выдачи (SITE-9):
 * webhook и планировщик ретраев не должны отправить RCON-команду по одному гранту дважды.
 */
class FulfillmentServiceTest {

    private VipGrantRepository grants;
    private RconClient rcon;
    private RconProperties props;
    private FulfillmentService service;

    @BeforeEach
    void setUp() {
        grants = mock(VipGrantRepository.class);
        rcon = mock(RconClient.class);
        props = new RconProperties();
        props.setEnabled(true);
        props.setMaxAttempts(10);

        service = new FulfillmentService(grants, rcon, props);
        ReflectionTestUtils.setField(service, "vipGroup", "VIP");
    }

    private VipGrant grant(GrantStatus status, int attempts) {
        VipGrant g = new VipGrant();
        g.setId(UUID.randomUUID());
        g.setPaymentId(UUID.randomUUID());
        g.setNick("Steve");
        g.setPlatform(Platform.JAVA);
        g.setDurationDays(30);
        g.setStatus(status);
        g.setAttempts(attempts);
        g.setCreatedAt(OffsetDateTime.now());
        return g;
    }

    @Test
    void deliversPendingGrantAndMarksDelivered() {
        VipGrant g = grant(GrantStatus.PENDING, 0);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));
        when(grants.claimForDelivery(eq(g.getId()), any())).thenReturn(1);
        when(rcon.execute(anyString())).thenReturn("ok");

        service.tryDeliver(g.getId());

        verify(rcon, times(1)).execute(anyString());
        assertThat(g.getStatus()).isEqualTo(GrantStatus.DELIVERED);
        assertThat(g.getDeliveredAt()).isNotNull();
        verify(grants).save(g);
    }

    @Test
    void lostClaimMeansNoRconCall() {
        VipGrant g = grant(GrantStatus.PENDING, 0);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));
        // Кто-то другой уже захватил грант: атомарный UPDATE не тронул ни одной строки.
        when(grants.claimForDelivery(eq(g.getId()), any())).thenReturn(0);

        service.tryDeliver(g.getId());

        verify(rcon, never()).execute(anyString());
        verify(grants, never()).save(any());
    }

    @Test
    void parallelDeliveryOfSameGrantSendsExactlyOneRconCommand() throws Exception {
        VipGrant g = grant(GrantStatus.PENDING, 0);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));
        when(rcon.execute(anyString())).thenReturn("ok");

        // Моделируем атомарность UPDATE ... WHERE status='PENDING':
        // ровно один из конкурентов получает 1, остальные — 0.
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(grants.claimForDelivery(eq(g.getId()), any()))
                .thenAnswer(inv -> claimed.compareAndSet(false, true) ? 1 : 0);

        // Гонка webhook vs планировщик: два параллельных deliver() одного гранта.
        CompletableFuture<Void> webhook = CompletableFuture.runAsync(() -> service.tryDeliver(g.getId()));
        CompletableFuture<Void> scheduler = CompletableFuture.runAsync(() -> service.tryDeliver(g.getId()));
        CompletableFuture.allOf(webhook, scheduler).join();

        verify(rcon, times(1)).execute(anyString());
    }

    @Test
    void rconFailureBelowMaxAttemptsReturnsGrantToQueue() {
        VipGrant g = grant(GrantStatus.PENDING, 0);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));
        when(grants.claimForDelivery(eq(g.getId()), any())).thenAnswer(inv -> {
            g.setStatus(GrantStatus.DELIVERING);
            g.setAttempts(g.getAttempts() + 1);
            return 1;
        });
        when(rcon.execute(anyString())).thenThrow(new RconException("server offline"));

        service.tryDeliver(g.getId());

        assertThat(g.getStatus()).isEqualTo(GrantStatus.PENDING);
        assertThat(g.getLastError()).contains("server offline");
        verify(grants).save(g);
    }

    @Test
    void rconFailureAtMaxAttemptsMarksGrantFailed() {
        props.setMaxAttempts(3);
        VipGrant g = grant(GrantStatus.PENDING, 2);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));
        when(grants.claimForDelivery(eq(g.getId()), any())).thenAnswer(inv -> {
            g.setStatus(GrantStatus.DELIVERING);
            g.setAttempts(g.getAttempts() + 1);
            return 1;
        });
        when(rcon.execute(anyString())).thenThrow(new RconException("still offline"));

        service.tryDeliver(g.getId());

        assertThat(g.getStatus()).isEqualTo(GrantStatus.FAILED);
        verify(grants).save(g);
    }

    @Test
    void rconDisabledLeavesGrantInQueue() {
        props.setEnabled(false);
        VipGrant g = grant(GrantStatus.PENDING, 0);
        when(grants.findById(g.getId())).thenReturn(Optional.of(g));

        service.tryDeliver(g.getId());

        verify(grants, never()).claimForDelivery(any(), any());
        verify(rcon, never()).execute(anyString());
        assertThat(g.getStatus()).isEqualTo(GrantStatus.PENDING);
    }
}
