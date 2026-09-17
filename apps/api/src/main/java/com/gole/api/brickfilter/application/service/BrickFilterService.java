package com.gole.api.brickfilter.application.service;

import com.gole.api.brickfilter.application.port.in.ManageBrickFilterUseCase;
import com.gole.api.brickfilter.application.port.out.*;
import com.gole.api.brickfilter.domain.model.BrickJob;
import com.gole.api.brickfilter.domain.model.BrickJob.*;
import com.gole.api.common.exception.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BrickFilterService implements ManageBrickFilterUseCase {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final BrickLedgerPort ledger;
    private final BrickBlobPort blobs;
    private final BrickImagePort images;
    private final BrickGeneratorPort generator;
    private final Clock clock;
    private final BrickProviderGatePort gate;
    private final Semaphore capacity = new Semaphore(4);

    @org.springframework.beans.factory.annotation.Autowired
    public BrickFilterService(
            BrickLedgerPort ledger,
            BrickBlobPort blobs,
            BrickImagePort images,
            BrickGeneratorPort generator,
            BrickProviderGatePort gate) {
        this(ledger, blobs, images, generator, gate, Clock.systemUTC());
    }

    public BrickFilterService(
            BrickLedgerPort ledger,
            BrickBlobPort blobs,
            BrickImagePort images,
            BrickGeneratorPort generator,
            BrickProviderGatePort gate,
            Clock clock) {
        this.ledger = ledger;
        this.blobs = blobs;
        this.images = images;
        this.generator = generator;
        this.clock = clock;
        this.gate = gate;
    }

    public Quota quota(String owner) {
        var day = today();
        recover(ledger.load(owner, day));
        return new Quota(
                day,
                Math.max(0, 3 - ledger.load(owner, day).occupied()),
                3,
                generator.enabled(),
                ledger.load(owner, day).attempts() < 30,
                LocalDate.parse(day).plusDays(1).atStartOfDay(SEOUL).toInstant());
    }

    public View submit(String owner, String id, Mode mode, byte[] input) {
        String day = dayOf(id);
        if (mode == null) throw new BadRequestException("BRICK_MODE", "모드를 선택해 주세요");
        String digest = hash(input) + ":" + mode.name();
        var before = ledger.load(owner, day);
        recover(before);
        var existing = find(ledger.load(owner, day), id);
        if (existing.isPresent()) return replay(existing.get(), digest);
        if (!day.equals(today())) throw new BadRequestException("BRICK_REQUEST_DAY", "서울 기준 오늘 날짜로 새 요청을 만들어 주세요");
        if (!generator.enabled()) throw new ServiceUnavailableException("BRICK_DISABLED", "브릭 필터를 준비 중입니다");
        if (!capacity.tryAcquire())
            throw new TooManyRequestsException("BRICK_BUSY", "잠시 후 다시 시도해 주세요", Duration.ofSeconds(10));
        boolean reserved = false;
        boolean providerLease = false;
        String providerToken = java.util.UUID.randomUUID().toString();
        try {
            byte[] source = images.sanitize(input, false);
            if (!day.equals(today()))
                throw new BadRequestException("BRICK_REQUEST_DAY", "날짜가 바뀌었습니다. 이용 횟수를 다시 확인해 주세요");
            Instant now = clock.instant();
            var job = new BrickJob(id, mode, digest, Status.RESERVED, now.plusSeconds(300), now.plusSeconds(86400));
            reserved = ledger.reserve(owner, day, job);
            if (!reserved) {
                var raced = find(ledger.load(owner, day), id);
                if (raced.isPresent()) return replay(raced.get(), digest);
                throw new TooManyRequestsException(
                        "BRICK_QUOTA",
                        "오늘 이용 가능한 횟수를 모두 사용했습니다",
                        Duration.between(
                                now,
                                LocalDate.parse(day)
                                        .plusDays(1)
                                        .atStartOfDay(SEOUL)
                                        .toInstant()));
            }
            providerLease = gate.acquire(providerToken, clock.instant());
            if (!providerLease) throw new ServiceUnavailableException("BRICK_BUSY", "이미지 생성 요청이 많습니다. 잠시 후 다시 시도해 주세요");
            blobs.put(owner, id, "source", source, job.leaseUntil());
            byte[] result = images.sanitize(generator.generate(source, mode), true);
            blobs.put(owner, id, "result", result, job.resultUntil());
            if (!ledger.complete(owner, day, id, clock.instant())) {
                failReservation(owner, day, id);
            }
        } catch (RuntimeException failure) {
            if (!reserved) throw failure;
            // No raw provider errors or user images reach logs or the public API.
            failReservation(owner, day, id);
        } finally {
            if (reserved) safeDelete(owner, id, "source");
            if (providerLease)
                try {
                    gate.release(providerToken);
                } catch (RuntimeException ignored) {
                    /* lease expiry releases capacity */
                }
            capacity.release();
        }
        return get(owner, id);
    }

    public List<View> recent(String owner) {
        var day = today();
        recover(ledger.load(owner, day));
        return ledger.load(owner, day).jobs().reversed().stream()
                .map(BrickFilterService::view)
                .toList();
    }

    public View get(String owner, String id) {
        String day = dayOf(id);
        recover(ledger.load(owner, day));
        return view(find(ledger.load(owner, day), id).orElseThrow(BrickFilterService::missing));
    }

    public byte[] result(String owner, String id) {
        var job = get(owner, id);
        if (job.status() != Status.SUCCEEDED || !clock.instant().isBefore(job.resultUntil())) throw missing();
        return blobs.get(owner, id, "result", clock.instant()).orElseThrow(BrickFilterService::missing);
    }

    @Scheduled(fixedDelayString = "${gole.brick-filter.cleanup-delay:60000}")
    public void cleanup() {
        for (var bucket : ledger.expired(clock.instant())) recover(bucket);
        blobs.purge(clock.instant());
        ledger.purge(clock.instant());
    }

    private void recover(BrickLedgerPort.Ledger bucket) {
        for (var job : bucket.jobs())
            if (job.status() == Status.RESERVED && !clock.instant().isBefore(job.leaseUntil())) {
                if (ledger.fail(bucket.owner(), bucket.day(), job.id())) {
                    safeDelete(bucket.owner(), job.id(), "source");
                    safeDelete(bucket.owner(), job.id(), "result");
                }
            }
    }

    private void failReservation(String owner, String day, String id) {
        boolean changed = ledger.fail(owner, day, id);
        // A Mongo commit may have succeeded before its response was lost. Never delete a committed result.
        if (changed
                || find(ledger.load(owner, day), id)
                        .map(j -> j.status() == Status.FAILED)
                        .orElse(false)) safeDelete(owner, id, "result");
    }

    private void safeDelete(String owner, String id, String kind) {
        try {
            blobs.delete(owner, id, kind);
        } catch (RuntimeException ignored) {
            /* Expiry sweep retries physical deletion. */
        }
    }

    private static View replay(BrickJob job, String digest) {
        if (!job.digest().equals(digest))
            throw new ConflictException("BRICK_IDEMPOTENCY_CONFLICT", "동일 요청 키의 사진이나 모드를 변경할 수 없습니다");
        return view(job);
    }

    private static View view(BrickJob j) {
        return new View(j.id(), j.mode(), j.status(), j.leaseUntil(), j.resultUntil());
    }

    private static Optional<BrickJob> find(BrickLedgerPort.Ledger l, String id) {
        return l.jobs().stream().filter(j -> j.id().equals(id)).findFirst();
    }

    private String today() {
        return LocalDate.now(clock.withZone(SEOUL)).toString();
    }

    private static String dayOf(String id) {
        if (id == null
                || !id.matches(
                        "[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BadRequestException("BRICK_REQUEST_ID", "요청 키가 올바르지 않습니다");
        try {
            return LocalDate.parse(id.substring(0, 10)).toString();
        } catch (DateTimeException e) {
            throw new BadRequestException("BRICK_REQUEST_ID", "요청 날짜가 올바르지 않습니다");
        }
    }

    private static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NotFoundException missing() {
        return new NotFoundException("BRICK_NOT_FOUND", "결과가 없거나 보관 기간이 지났습니다");
    }
}
