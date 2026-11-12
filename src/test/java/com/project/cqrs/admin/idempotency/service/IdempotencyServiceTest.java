package com.project.cqrs.admin.idempotency.service;
import com.project.cqrs.admin.idempotency.entity.*;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class IdempotencyServiceTest {
    ProcessedEventRepository repository; IdempotencyService service;
    @BeforeEach void setup() { repository = mock(ProcessedEventRepository.class); service = new IdempotencyService(repository); }
    @Test void claimsNewEvent() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.tryClaim("e", "t", 1);
        assertThat(result.getStatus()).isEqualTo(EventStatus.PROCESSING);
    }
    @Test void skipsCompletedEvent() {
        var event = ProcessedEventEntity.claim("e", "t"); event.markCompleted();
        when(repository.findByEventIdAndTopic("e", "t")).thenReturn(Optional.of(event));
        assertThat(service.tryClaim("e", "t", 2)).isNull(); verify(repository, never()).save(any());
    }
    @Test void rejectsConcurrentProcessing() {
        when(repository.findByEventIdAndTopic("e", "t")).thenReturn(Optional.of(ProcessedEventEntity.claim("e", "t")));
        assertThatThrownBy(() -> service.tryClaim("e", "t", 1)).isInstanceOf(IllegalStateException.class);
    }
    @Test void letsConstraintViolationRollbackAndRetry() {
        when(repository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertThatThrownBy(() -> service.tryClaim("e", "t", 1)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void retriesFailedEvent() {
        var event = ProcessedEventEntity.claim("e", "t"); event.markFailed("failure");
        when(repository.findByEventIdAndTopic("e", "t")).thenReturn(Optional.of(event)); when(repository.save(event)).thenReturn(event);
        assertThat(service.tryClaim("e", "t", 2).getStatus()).isEqualTo(EventStatus.PROCESSING);
    }
}