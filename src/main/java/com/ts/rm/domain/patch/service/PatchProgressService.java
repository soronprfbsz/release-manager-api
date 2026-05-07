package com.ts.rm.domain.patch.service;

import com.ts.rm.domain.patch.dto.PatchDto;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 패치 생성 진행 상황 추적 서비스 (in-memory).
 *
 * <p>frontend 가 mutation 호출 시 X-Progress-Id 헤더 (UUID) 를 보내고,
 * 같은 ID 로 GET /api/patches/progress/{id} 를 1초 polling. backend 는
 * 단계 메서드 안에서 {@link #update} 만 호출하면 ThreadLocal 로 현재 진행 ID
 * 를 따라가 메모리 Map 에 저장한다.
 *
 * <p>저장소: ConcurrentMap (in-memory) — 패치 생성은 보통 30초~수 분 단위라
 * 영구 저장 불필요. 서버 재시작 시 진행 중 패치는 추적 불가능 (단 mutation 도
 * 끊기므로 영향 동일). multi-instance 운영 시 Redis 로 이전 가능.
 *
 * <p>ThreadLocal 사용처:
 * <ul>
 *   <li>{@link #start} 로 진행 ID 설정 (Controller 진입 시점)</li>
 *   <li>{@link #update} 는 인자 없이 ThreadLocal 의 ID 로 자동 매핑 — Service
 *       메서드 시그니처 변경 0</li>
 *   <li>{@link #end} 로 ThreadLocal 정리 + 결과 보존 옵션 (try-finally 필수)</li>
 * </ul>
 *
 * <p>같은 thread 내에서만 동작. Spring 의 동기 @Transactional 흐름은 안전.
 * @Async / 별도 스레드 사용 시 ThreadLocal 전파 안 됨에 유의.
 */
@Slf4j
@Service
public class PatchProgressService {

    /** progressId → 진행 상황 (singleton) */
    private final ConcurrentMap<String, PatchDto.PatchProgress> progressMap =
            new ConcurrentHashMap<>();

    /** 현재 thread 의 진행 ID */
    private final ThreadLocal<String> currentId = new ThreadLocal<>();

    /**
     * 진행 추적 시작. Controller 진입 시점에 한 번 호출.
     * try-finally 로 반드시 {@link #end} 와 짝 맞춰야 ThreadLocal 누수 방지.
     */
    public void start(String progressId) {
        if (progressId == null || progressId.isBlank()) return;
        currentId.set(progressId);
        progressMap.put(progressId, new PatchDto.PatchProgress(0, 0, "시작", false));
    }

    /**
     * 단계 갱신. ThreadLocal 의 progressId 가 비어있으면 no-op.
     */
    public void update(int step, int totalSteps, String message) {
        String id = currentId.get();
        if (id == null || id.isBlank()) return;
        progressMap.put(id, new PatchDto.PatchProgress(step, totalSteps, message, false));
        log.debug("patch progress: id={} step={}/{} {}", id, step, totalSteps, message);
    }

    /**
     * 완료 표시. polling 이 'completed=true' 를 받으면 중단 + 결과 처리.
     * <p>ThreadLocal 정리 / 메모리 정리는 {@link #end} 에서.
     */
    public void complete(int totalSteps) {
        String id = currentId.get();
        if (id == null || id.isBlank()) return;
        progressMap.put(id,
                new PatchDto.PatchProgress(totalSteps, totalSteps, "완료", true));
    }

    /**
     * 실패 표시.
     */
    public void fail(String reason) {
        String id = currentId.get();
        if (id == null || id.isBlank()) return;
        PatchDto.PatchProgress prev = progressMap.get(id);
        int total = prev != null ? prev.totalSteps() : 1;
        int step = prev != null ? prev.step() : 0;
        progressMap.put(id,
                new PatchDto.PatchProgress(step, total, "실패: " + reason, true));
    }

    /**
     * ThreadLocal 정리 + 잠시 후 메모리 cleanup.
     * <p>frontend 가 마지막 polling 으로 completed=true 를 받을 시간을 주기 위해
     * 즉시 삭제 안 함. 10초 후 cleanup.
     */
    public void end() {
        String id = currentId.get();
        currentId.remove();
        if (id == null || id.isBlank()) return;
        // 10초 후 메모리에서 제거 (frontend 가 마지막 polling 으로 completed 받을 시간)
        new Thread(() -> {
            try { Thread.sleep(10_000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            progressMap.remove(id);
        }, "patch-progress-cleanup-" + id).start();
    }

    /**
     * 폴링 응답용 조회. 없으면 null.
     */
    public PatchDto.PatchProgress get(String progressId) {
        if (progressId == null || progressId.isBlank()) return null;
        return progressMap.get(progressId);
    }
}
