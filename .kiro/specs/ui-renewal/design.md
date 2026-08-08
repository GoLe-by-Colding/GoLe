# UI Renewal Design

## Visual Direction

GoLe의 Royal Blue와 Brick Gold는 유지한다. 장식 효과의 중첩 대신 타이포그래피, 간격, 선명한 테두리와 제한된 색상으로 위계를 만든다. 브랜드 배경은 단색 면으로 사용해 정체성을 살리고, 그 위에 패턴·글로우·그라디언트를 겹치지 않는다.

## Component Rules

| 영역   | 기준                                                       |
| ------ | ---------------------------------------------------------- |
| Button | 10px radius, 단색, 색상 전환만 사용                        |
| Input  | 10px radius, 1px border, 명확한 focus ring                 |
| Card   | 14px radius, 1px border, 기본 shadow 없음                  |
| Badge  | 상태와 짧은 분류에만 pill 허용                             |
| Header | solid white, bottom border, active underline               |
| Footer | solid deep navy, 1px top border                            |
| Motion | 150ms 색상 전환을 기본으로 하며 시세 티커만 자동 흐름 허용 |

## Screen Changes

- 인증: 옅은 브랜드 단색 배경을 사용하고, 탭을 밑줄 방식으로 단순화한다.
- 소셜 로그인: 버튼 전체를 제공자 색으로 채우지 않고 작은 제공자 마크에만 색을 사용한다.
- 홈 히어로: 딥 네이비 단색 배경은 유지하되 오션 그라디언트, 도트 패턴, 워터마크, 이모지와 등장 애니메이션을 제거한다.
- 시세: 같은 데이터를 연속 티커로 표시하고 hover 시 일시정지하며 reduced motion 설정을 따른다.
- 카드 목록: hover 이동·확대와 순위 그라디언트를 제거한다.
- 하단 CTA와 푸터: 복합 배경 대신 각각 단일 표면을 사용한다.

## Allowed Exceptions

- 데이터 시각화 SVG 내부의 그라디언트
- 외부 로그인 제공자를 식별하는 작은 브랜드 마크
- 아바타와 상태 배지의 `rounded-full`
- 오버레이, 팝오버, 모달의 목적성 있는 그림자
- 홈 시세 정보의 연속적인 자동 흐름
