-- ReleaseVersionControllerTreeTest용 테스트 데이터

-- 기존 데이터 삭제 (테스트 격리)
DELETE FROM release_file;
DELETE FROM release_version_hierarchy;
DELETE FROM release_version;
DELETE FROM project;

-- 테스트용 프로젝트 데이터 (release_version.project_id NOT NULL 충족)
INSERT INTO project (project_id, project_name, is_enabled, created_at, updated_at)
VALUES ('test-project', 'Test Project', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 테스트용 릴리즈 버전 데이터 생성 (ID 명시적 지정)
-- 주의: release_version 테이블에는 created_at만 있고 updated_at은 없음
INSERT INTO release_version (release_version_id, project_id, version, release_type, major_version, minor_version, patch_version, is_approved, hotfix_version, build_version, created_by_email, comment, created_at)
VALUES
    (1, 'test-project', '1.1.0', 'STANDARD', 1, 1, 0, FALSE, 0, 0, 'jhlee@tscientific', 'Initial version', CURRENT_TIMESTAMP),
    (2, 'test-project', '1.1.1', 'STANDARD', 1, 1, 1, FALSE, 0, 0, 'jhlee@tscientific', 'Bug fix', CURRENT_TIMESTAMP),
    (3, 'test-project', '1.2.0', 'STANDARD', 1, 2, 0, FALSE, 0, 0, 'jhlee@tscientific', 'New features', CURRENT_TIMESTAMP),
    (4, 'test-project', '1.2.1', 'STANDARD', 1, 2, 1, FALSE, 0, 0, 'jhlee@tscientific', 'Hotfix', CURRENT_TIMESTAMP),
    (5, 'test-project', '2.0.0', 'STANDARD', 2, 0, 0, FALSE, 0, 0, 'jhlee@tscientific', 'Major release', CURRENT_TIMESTAMP);

-- 계층 구조 데이터 추가 (Closure Table 패턴)
-- 각 버전은 자기 자신과의 관계 (depth=0) 필수
INSERT INTO release_version_hierarchy (ancestor_id, descendant_id, depth) VALUES
-- 1.1.0 (ID=1): 자기 자신
(1, 1, 0),
-- 1.1.1 (ID=2): 자기 자신
(2, 2, 0),
-- 1.2.0 (ID=3): 자기 자신
(3, 3, 0),
-- 1.2.1 (ID=4): 자기 자신
(4, 4, 0),
-- 2.0.0 (ID=5): 자기 자신
(5, 5, 0);
