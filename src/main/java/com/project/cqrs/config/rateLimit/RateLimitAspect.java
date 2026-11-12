package com.project.cqrs.config.rateLimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class RateLimitAspect {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint,
                                 RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes()).getRequest();

        String key = resolveClientKey(request) + ":" +
                joinPoint.getSignature().toShortString();

        Bucket bucket = buckets.getIfPresent(key);
        if (bucket == null) {
            bucket = buckets.get(key, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(
                            rateLimit.requests(),
                            Refill.greedy(rateLimit.requests(),
                                    Duration.ofSeconds(rateLimit.durationSeconds()))
                    ))
                    .build());
        }

        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        }

        throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
    }

    /**
     * Resolve a chave do cliente para o rate limit.
     *
     * ATENÇÃO: request.getRemoteAddr() atrás de um proxy/load balancer
     * (nginx, ALB, Cloudflare) retorna o IP do PRÓPRIO PROXY, não do
     * cliente real — todo mundo compartilharia o mesmo bucket. Se o
     * projeto for rodar atrás de proxy reverso, use o header
     * X-Forwarded-For (com validação — é um header que o cliente
     * pode forjar se o proxy não sobrescrever).
     *
     * Para endpoints autenticados, prefira limitar por userId
     * (extraído do JWT via SecurityContextHolder) em vez de IP —
     * mais preciso e imune a NAT compartilhado.
     */
    private String resolveClientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
