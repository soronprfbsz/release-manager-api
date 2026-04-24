package com.ts.rm.domain.patch.util;

import com.ts.rm.domain.releasefile.entity.ReleaseFile;
import com.ts.rm.domain.releaseversion.entity.ReleaseVersion;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * InfraEye CLI 교체 파일 생성 구현체
 *
 * <p>레거시 {@code /usr/bin/InfraEye} (bash CLI)를 Release Manager 1.0.0 패치로 교체하기 위한
 * {@code InfraEye} 파일을 패치 디렉토리 루트에 생성합니다.
 *
 * <p>이 Generator는 {@code fromVersion=1.0.0} 패치에만 포함됩니다.
 * 치환 토큰이 없으므로 템플릿 파일을 그대로 복사합니다.
 *
 * <p>생성 파일: {@code InfraEye} (확장자 없음, 실행 권한 부여)
 */
@Slf4j
@Component("infraEyeCliScriptGenerator")
public class InfraEyeCliScriptGenerator extends AbstractScriptGenerator {

    @Override
    protected String getTemplatePath() {
        return "templates/COMMON/infraeye_cli_template.sh";
    }

    @Override
    protected String getDatabaseType() {
        return "InfraEye CLI";
    }

    @Override
    public String getScriptFileName() {
        // 확장자 없음: /usr/bin/InfraEye 교체 대상과 동일한 파일명
        return "InfraEye";
    }

    /**
     * InfraEye CLI 교체 파일 생성
     *
     * <p>치환 토큰이 없으므로 템플릿 파일을 그대로 {@code outputDirPath/InfraEye}로 복사합니다.
     *
     * @param outputDirPath 출력 디렉토리 경로 (baseReleasePath 기준 상대 경로)
     */
    public void generate(String outputDirPath) {
        // 템플릿 로드 (치환 토큰 없음 — 그대로 사용)
        String content = loadTemplate();

        // 스크립트 저장 (setExecutable(true) 포함, Linux/Mac 환경에서만 적용)
        saveScript(content, outputDirPath);

        log.info("InfraEye CLI 교체 파일 생성 완료: {}/{}", outputDirPath, getScriptFileName());
    }

    /**
     * 패치 스크립트 생성 — InfraEye CLI는 이 메서드를 사용하지 않습니다.
     *
     * <p>{@link ScriptGenerator} 인터페이스 계약을 이행하나,
     * InfraEye CLI는 {@link #generate(String)} 메서드로만 사용하세요.
     */
    @Override
    public void generatePatchScript(
            String projectId,
            String fromVersion,
            String toVersion,
            List<ReleaseVersion> versions,
            List<ReleaseFile> files,
            String outputDirPath,
            String defaultPatchedBy) {
        generate(outputDirPath);
    }

    /**
     * 핫픽스 스크립트 생성 — InfraEye CLI는 이 메서드를 사용하지 않습니다.
     *
     * <p>{@link ScriptGenerator} 인터페이스 계약을 이행하나, 핫픽스에는 InfraEye CLI가 포함되지 않습니다.
     */
    @Override
    public void generateHotfixScript(
            String projectId,
            ReleaseVersion hotfixVersion,
            List<ReleaseFile> files,
            String outputDirPath,
            String defaultPatchedBy) {
        // 핫픽스에는 InfraEye CLI를 포함하지 않음 — 의도적으로 비워둡니다.
        log.debug("InfraEye CLI 핫픽스 스크립트 생성 미지원 — 건너뜁니다.");
    }
}
