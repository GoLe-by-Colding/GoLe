# GoLe — LEGO Marketplace

레고 중고거래 플랫폼. KREAM(시세/검수/안전거래) · 당근(동네 직거래/매너온도) · 콜리(컬렉션/자랑) · 후르츠패밀리(셀러 샵/팔로우/큐레이션)의 강점을 결합한 모노레포 프로젝트.

## Stack

| 영역 | 기술 |
| --- | --- |
| Frontend | Next.js 16 (App Router, React 19, Turbopack), TypeScript strict, FSD 아키텍처, Playwright E2E |
| Backend | Spring Boot 4 (Spring Framework 7), Java 21 LTS, 헥사고날 아키텍처 + AOP |
| Data | MongoDB (primary), Redis (cache/chat/ranking) |
| Infra | Docker Compose (mongo, redis), pnpm workspace 모노레포 |

> 저장소 결정: 초기엔 MongoDB + Redis만 사용. 정산 도메인이 관계형 정합성을 요구하게 되면 헥사고날 포트/어댑터 덕분에 해당 컨텍스트에만 PostgreSQL 어댑터를 추가할 수 있음.

## Repository Layout

```
GoLe/
├── apps/
│   ├── web/        # Next.js 16 프론트엔드 (FSD)
│   └── api/        # Spring Boot 4 백엔드 (헥사고날)
├── docker-compose.yml
├── pnpm-workspace.yaml
└── package.json
```

## Getting Started

```bash
# 0. 인프라 기동 (MongoDB + Redis)
docker compose up -d

# 1. 프론트엔드
pnpm install
pnpm --filter web dev          # http://localhost:3000

# 2. 백엔드
cd apps/api && ./gradlew bootRun   # http://localhost:8080
```

## Spec

기획/요구사항 문서: `.kiro/specs/lego-marketplace/requirements.md`
