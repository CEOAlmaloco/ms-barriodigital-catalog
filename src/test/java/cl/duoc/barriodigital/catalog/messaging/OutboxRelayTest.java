package cl.duoc.barriodigital.catalog.messaging;

import cl.duoc.barriodigital.catalog.persistence.OutboxEvent;
import cl.duoc.barriodigital.catalog.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void publicaEnOrdenYMarcaPublishedAt() throws Exception {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        AuditKafkaSender sender = mock(AuditKafkaSender.class);
        TransactionTemplate tx = passthroughTx();

        OutboxEvent first = new OutboxEvent("e1", "procedure-type:a", "procedure-type.created", "{}", Instant.now(CLOCK));
        OutboxEvent second = new OutboxEvent("e2", "procedure-type:b", "procedure-type.updated", "{}", Instant.now(CLOCK));
        when(repo.findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc()).thenReturn(List.of(first, second));
        when(repo.findById("e1")).thenReturn(Optional.of(first));
        when(repo.findById("e2")).thenReturn(Optional.of(second));
        when(repo.countByPublishedAtIsNull()).thenReturn(0L);

        new OutboxRelay(repo, sender, CLOCK, tx).publishPending();

        verify(sender).sendConfirmed(OutboxRelay.TOPIC, "procedure-type:a", "{}");
        verify(sender).sendConfirmed(OutboxRelay.TOPIC, "procedure-type:b", "{}");
        verify(repo).save(first);
        verify(repo).save(second);
    }

    @Test
    void anteFallaNoContinuaConElSiguiente() throws Exception {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        AuditKafkaSender sender = mock(AuditKafkaSender.class);
        TransactionTemplate tx = passthroughTx();

        OutboxEvent first = new OutboxEvent("e1", "procedure-type:a", "procedure-type.created", "{}", Instant.now(CLOCK));
        OutboxEvent second = new OutboxEvent("e2", "procedure-type:b", "procedure-type.updated", "{}", Instant.now(CLOCK));
        when(repo.findFirst100ByPublishedAtIsNullOrderByEventSequenceAscCreatedAtAsc()).thenReturn(List.of(first, second));
        when(repo.findById("e1")).thenReturn(Optional.of(first));
        doThrow(new RuntimeException("broker down")).when(sender)
                .sendConfirmed(eq(OutboxRelay.TOPIC), eq("procedure-type:a"), anyString());

        new OutboxRelay(repo, sender, CLOCK, tx).publishPending();

        verify(sender, never()).sendConfirmed(eq(OutboxRelay.TOPIC), eq("procedure-type:b"), anyString());
        verify(repo).save(first);
    }

    @SuppressWarnings("unchecked")
    private static TransactionTemplate passthroughTx() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        return tx;
    }
}
