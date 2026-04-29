# InfraEye CLI 1.0.0 설치

이 패치본을 처음 받았다면 아래 3 단계를 그대로 따라하세요.

## 1. 패치본을 정해진 위치에 풀기

```
/opt/infraeye/patch/<버전>/
```

예: `/opt/infraeye/patch/1.0.0/`. 풀고 나면 그 안에 `etc/InfraEye` 파일이 보여야 합니다.

## 2. CLI 설치 (최초 1회, sudo 필수)

```bash
sudo /opt/infraeye/patch/<버전>/etc/InfraEye cli patch
```

- `/usr/bin/InfraEye` 가 새 1.0.0 으로 교체됩니다.
- 기존 바이너리는 `/usr/bin/InfraEye.legacy-bak` 에 자동 백업됩니다.
- `INSTALL_PATH` / `NMS_CONTAINER_NM` 은 기존 바이너리에서 자동 승계됩니다.

## 3. 동작 확인

```bash
InfraEye --version
```

다음이 출력되면 성공:

```
InfraEye CLI 1.0.0 (Release Manager edition)
```

---

## 자주 쓰는 옵션

| 명령 | 의미 |
|---|---|
| `sudo InfraEye cli patch --force` | 같은 버전이어도 다시 설치 |
| `sudo InfraEye cli patch --rollback` | `/usr/bin/InfraEye.legacy-bak` 으로 복원 |
| `sudo InfraEye cli patch --help` | 전체 옵션 보기 |

## 안 될 때

| 메시지 | 해결 |
|---|---|
| `root 권한 필요` | `sudo` 를 빠뜨렸음. 다시 실행 |
| `CLI 소스 파일(InfraEye)을 찾을 수 없습니다` | 1 단계의 압축 위치 확인. `/opt/infraeye/patch/` 아래에 `InfraEye` 파일이 보여야 함 |
| `CLI 소스 파일이 2개 이상 발견되어` | 패치 디렉토리에 InfraEye 파일이 여러 개. 하나만 남기고 나머지는 제거 |
| `이미 1.0.0 설치됨. skip.` | 정상. 강제로 다시 설치하려면 `--force` |
