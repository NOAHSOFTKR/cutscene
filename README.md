# CutThin

Paper/Spigot 1.21.4용 컷신 재생 플러그인. 아르카나 서버 메인 스토리에서 핵심 전환점에 인게임 연출(블라인드, 채팅 시퀀스, 매직폰트 타이틀, 파티클, 텔레포트 등)을 재생하기 위해 만들어졌다.

## 빌드

```bash
mvn clean package
```

`target/cutthin-1.0.0.jar` → 서버 `plugins/` 디렉토리에 복사.

## 파일 구조 (런타임)

```
plugins/CutThin/
├── config.yml                # 기본 설정
└── cutscenes/                # YAML 컷신 정의 (자유롭게 추가)
    ├── arcana-prologue-exile.yml
    ├── arcana-memory-restore.yml
    └── arcana-final-boss.yml
```

처음 활성화 시 `cutscenes/` 폴더가 없으면 jar에 포함된 3개의 기본 컷신이 자동으로 추출된다.

## 컷신 YAML 포맷

```yaml
id: my-cutscene             # 고유 ID (영문/숫자/하이픈)
name: "사람이 읽는 이름"
freeze: true                # 재생 중 플레이어 이동/상호작용/데미지 차단
steps:
  - { type: <step-type>, ... }
  - ...
```

### Step 타입

| type | 필드 | 설명 |
|------|------|------|
| `wait` | `ticks: Long` | 다음 step까지 딜레이 (20틱 = 1초) |
| `chat` | `message: String`, `target: PLAYER\|ALL` | 채팅 메시지. `&` 색상코드 지원 |
| `title` | `title`, `subtitle: String`, `fadeIn`, `stay`, `fadeOut: Int` | 화면 중앙 타이틀. `&k` 매직폰트 가능 |
| `actionbar` | `text: String` | 액션바 메시지 |
| `effect` | `effect: PotionEffectType`, `duration`, `amplifier: Int` | 포션 효과 부여 |
| `clear_effects` | `effects: List<String>?` | 효과 제거 (생략 시 전부) |
| `sound` | `sound: Sound`, `volume`, `pitch: Float` | 사운드 재생 |
| `particle` | `particle: Particle`, `count: Int`, `offsetX/Y/Z`, `speed: Double`, `at: PLAYER\|ABSOLUTE` | 파티클 |
| `clear_inventory` | — | 인벤토리 + 방어구 + 보조손 **영구 삭제** (추방 컷신용) |
| `hide_inventory` | — | 인벤토리를 **임시 보관**하고 비움. 컷신 종료 시 자동 복원 |
| `show_inventory` | — | 숨긴 인벤토리를 즉시 복원 (중간 복원용, 선택) |
| `teleport` | `world`, `x/y/z: Double`, `randomRadius: Int?`, `safeY: Boolean` | 텔레포트 |
| `command` | `command: String` | 콘솔 권한으로 명령 실행. `{player}` 치환 가능 |
| `fire_event` | `key: String`, `placeholders: Map` | `CutsceneFireEvent` 발행. 스토리 플러그인이 리스닝 |
| `move` | `target: {world?, x, y, z}`, `duration_ticks: Int`, `easing: LINEAR\|EASE_IN\|EASE_OUT\|EASE_IN_OUT`, `preserve_look: Boolean` | 벡터 보간으로 부드럽게 이동. 매 틱 텔레포트 |
| `look_at` | `target: {world?, x, y, z}`, `duration_ticks: Int`, `easing` | 시선만 보간하여 대상을 향해 회전 |
| `velocity` | `x`, `y`, `z: Double`, `add: Boolean` | 일회성 벡터 적용 (`add: true`면 기존 velocity에 더함) |
| `clear_chat` | — | 100줄 공백을 보내 이전 채팅을 화면 밖으로 밀어냄 |

### 플레이스홀더

문자열을 받는 step (`chat`, `title`, `actionbar`, `command`, `fire_event`의 placeholders 값)에서 사용 가능:

| 형식 | 출처 | 비고 |
|------|------|------|
| `{player}`, `{cutscene}`, `{cutscene_name}` | CutThin 내장 | 항상 동작 |
| `%player_name%`, `%server_online%` 등 | PlaceholderAPI | PAPI가 로드돼 있을 때만 |
| `%cutthin:active%`, `%story:current_id%` 등 | 등록된 확장 | 해당 확장이 등록돼 있을 때 |

PAPI가 미설치면 `%...%` 문자열은 그대로 출력됩니다.

### `hide_inventory` 복원 정책

`hide_inventory`는 다음 시점에 자동으로 인벤토리를 돌려준다:

| 시나리오 | 동작 |
|---------|------|
| 컷신 정상 완료 | 즉시 복원 |
| `/cutscene stop` 으로 중단 | 즉시 복원 |
| 플레이어 퇴장 (`PLAYER_QUIT`) | 퇴장 처리 직전에 복원 → 다음 접속 시 그대로 보유 |
| 플레이어 사망 (`PLAYER_DEATH`) | 보관 중이던 아이템이 `PlayerDeathEvent.getDrops()`에 추가됨 → 평소처럼 사망 위치에 드랍 |
| 컷신 도중 에러 | 즉시 복원 |
| 서버 종료 (`onDisable`) | 온라인 플레이어에게 즉시 복원, 오프라인은 경고 로그 (현재 디스크 영속화 미지원) |

`clear_inventory`와 명확히 구분:
- `clear_inventory` → 영구 삭제 (1단계 추방처럼 의도된 분실)
- `hide_inventory` → 임시 숨김 + 자동 복원 (연출 목적)

## 명령어

| 명령 | 권한 | 설명 |
|------|------|------|
| `/cutscene play <id> [player]` | `cutthin.play` | 컷신 재생 |
| `/cutscene stop [player]` | `cutthin.stop` | 컷신 중단 |
| `/cutscene list` | `cutthin.list` | 등록된 컷신 목록 |
| `/cutscene info <id>` | `cutthin.info` | 컷신 정보 (step 수, 총 길이) |
| `/cutscene reload` | `cutthin.reload` | YAML 재로드 |

별칭: `/cs`, `/cut`.

## 스토리 플러그인 연동

### 방식 A — 명령어 호출 (스토리 플러그인 수정 불필요)

스토리 액션 YAML에서 기존 `commands` 액션을 그대로 사용:

```yaml
# story/actions/arcana-prologue-exile.yml
actions:
  commands:
    id: cmd-cutscene
    enable: true
    list:
      - "cutscene play arcana-prologue-exile {player}"
```

### 방식 B — API 직접 호출

스토리 플러그인의 코드에서 직접 호출 가능. CutThin이 `softdepend`라면 다음과 같이 사용:

```kotlin
import kr.kjh9211.cutthin.api.CutThinAPI

val result = CutThinAPI.play(player, "arcana-prologue-exile")
when (result) {
    CutThinAPI.PlayResult.SUCCESS -> { /* ... */ }
    CutThinAPI.PlayResult.NOT_FOUND -> { /* ... */ }
    CutThinAPI.PlayResult.ALREADY_PLAYING -> { /* ... */ }
    CutThinAPI.PlayResult.CANCELLED -> { /* ... */ }
    CutThinAPI.PlayResult.NOT_READY -> { /* ... */ }
}
```

### 컷신 종료 후 스토리 진행

#### 이벤트 방식

```kotlin
@EventHandler
fun onCutsceneEnd(event: CutsceneEndEvent) {
    if (event.reason != CutsceneEndEvent.Reason.COMPLETED) return
    // event.cutscene.id 로 분기, 다음 story 트리거 실행
    storyRuntime.runTriggerByEvent(
        "cutscene_end:${event.cutscene.id}",
        StoryExecutionContext(event.player, mapOf("player" to event.player.name))
    )
}

@EventHandler
fun onCutsceneFire(event: CutsceneFireEvent) {
    storyRuntime.runTriggerByEvent(
        event.key,
        StoryExecutionContext(event.player, event.placeholders)
    )
}
```

#### 마지막 step의 명령어 방식

```yaml
# 컷신 YAML의 마지막 step
- type: command
  command: "story trigger run arcana-prologue-exile prologue-aftermath"
```

## 설정 (`config.yml`)

```yaml
debug: false
default-fade:
  in: 10
  stay: 60
  out: 20
prevent-during-cutscene:
  movement: true
  interaction: true
  damage: true
  inventory: true
  drop: true
  command: true
allow-concurrent: false
```

## API 참조

```kotlin
object CutThinAPI {
    fun play(player: Player, cutsceneId: String): PlayResult
    fun stop(player: Player): Boolean
    fun stop(playerId: UUID): Boolean
    fun isPlaying(player: Player): Boolean
    fun currentCutscene(player: Player): String?
    fun registerCutscene(cutscene: Cutscene)   // 코드로 동적 등록
    fun cutsceneIds(): Set<String>
    fun cutscene(id: String): Cutscene?
    fun reload()
}
```

## 이벤트

| 이벤트 | 발행 시점 | Cancellable |
|--------|-----------|-------------|
| `CutsceneStartEvent` | 컷신 시작 직전 | ✅ |
| `CutsceneEndEvent` | 컷신 종료 (완료/중단/사망/퇴장/에러) | ❌ |
| `CutsceneFireEvent` | `fire_event` step 실행 시 | ❌ |

## 락 / 방해 차단

`freeze: true` 컷신 진행 중 자동 차단되는 동작 (모두 config로 토글 가능):

| 항목 | config 키 | 메커니즘 |
|------|-----------|----------|
| 이동 | `prevent-during-cutscene.movement` | `PlayerMoveEvent`에서 위치 변경 취소. 시선 회전은 허용. 컷신 자체 `move` step의 텔레포트는 통과 |
| 우클릭/상호작용 | `prevent-during-cutscene.interaction` | `PlayerInteractEvent` cancel |
| 데미지 | `prevent-during-cutscene.damage` | `EntityDamageEvent` / `EntityDamageByEntityEvent` cancel |
| 인벤토리 클릭 | `prevent-during-cutscene.inventory` | `InventoryClickEvent` cancel |
| 인벤토리 / 상자 **열기** | `prevent-during-cutscene.inventory-open` | `InventoryOpenEvent` cancel — UI 자체가 안 열림 |
| 아이템 드롭 | `prevent-during-cutscene.drop` | `PlayerDropItemEvent` cancel |
| 일반 플레이어 명령어 | `prevent-during-cutscene.command` | op 예외. `/cutscene` 계열은 허용 |
| 게임모드 전환 (관전모드 등) | `prevent-during-cutscene.gamemode-change` | `PlayerGameModeChangeEvent` cancel |
| 공개 채팅 수신 | `prevent-during-cutscene.receive-messages` | `AsyncPlayerChatEvent.recipients` 필터 |
| 귓속말 수신 | `prevent-during-cutscene.receive-whispers` | `/msg /tell /w /whisper /pm` 가로채기 + 발신자에게 "수신 불가" 알림 |
| 시스템 메시지 수신 | `prevent-during-cutscene.receive-system` | 입퇴장/사망 메시지의 `message`를 null로 세팅 후 non-locked에게만 재발송 |
| Tab 키 | `tab-mode` | 아래 참조 |

### Tab 키 모드

```yaml
tab-mode: HEADER_FOOTER   # HEADER_FOOTER | HIDE_PLAYERS | NONE
tab-header: "&c&l컷신 진행 중\n&7잠시만 기다려주세요\n\n"
tab-footer: "\n\n&8(컷신이 끝날 때까지 잠시만)"
```

- **HEADER_FOOTER** (기본) — Tab 누르면 상단에 "컷신 진행 중" 안내. 여러 줄 공백으로 실제 플레이어 목록을 화면 밖으로 밀어냄. 다른 플레이어 모습은 그대로 보임.
- **HIDE_PLAYERS** — 컷신 중 다른 플레이어가 세상과 Tab에서 모두 사라짐. 완전 시네마틱.
- **NONE** — Tab 처리 비활성화.

### 채팅 청소

```yaml
auto-clear-chat-on-start: true
```

freeze 컷신 시작 시 자동으로 100줄 공백을 푸시. YAML에서 `{ type: clear_chat }` step으로 임의 시점에 수동 호출도 가능.

### 귓속말 발신자 알림

```yaml
whisper-commands: ["/msg", "/tell", "/w", "/whisper", "/pm"]
whisper-blocked-message: "&c{target}님은 지금 메시지를 받을 수 없는 상태입니다."
```

`{target}`은 수신자 이름으로 치환.

### ProtocolLib (선택 의존성)

ProtocolLib이 설치돼 있으면 자동 감지·등록되어 **모든 outgoing chat 패킷**(SYSTEM_CHAT, CHAT, DISGUISED_CHAT)을 차단:

- 다른 플러그인이 `Player.sendMessage()`로 직접 보내는 메시지
- `Bukkit.broadcastMessage()`로 보낸 메시지
- 이벤트 없이 전송되는 모든 채팅

→ 락 플레이어에게 도달하지 않음. 컷신 자체 채팅은 bypass set으로 통과.

미설치 시 이벤트 기반 차단만 작동 (공개채팅 / 귓속말 / 시스템 메시지 / `/say`).

## PlaceholderAPI 확장 (`%cutthin:*%`)

PAPI가 설치돼 있으면 자동 등록된다. 다른 플러그인이나 스코어보드/홀로그램에서 사용 가능:

| 플레이스홀더 | 반환 | 비고 |
|--------------|------|------|
| `%cutthin:active%` | `true` / `false` | 현재 컷신 재생 중인지 |
| `%cutthin:active:<id>%` | `true` / `false` | 특정 ID의 컷신을 재생 중인지 |
| `%cutthin:current%` | 컷신 ID 또는 빈 문자열 | `current_id`와 동일 |
| `%cutthin:current_name%` | 컷신 표시 이름 | |
| `%cutthin:step_index%` | 현재 step 인덱스 (0-base) | 비재생 시 `-1` |
| `%cutthin:total_steps%` | 현재 컷신의 총 step 수 | 비재생 시 `0` |
| `%cutthin:progress%` | `현재/총` 형식 | 비재생 시 `0/0` |
| `%cutthin:count%` | 등록된 컷신 수 | |
| `%cutthin:exists:<id>%` | `true` / `false` | 해당 ID의 컷신이 등록돼 있는지 |
| `%cutthin:name:<id>%` | 해당 컷신의 표시 이름 | |
| `%cutthin:steps:<id>%` | 해당 컷신의 step 수 | |

## 라이선스

내부용.
