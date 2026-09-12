# EXE201 — mirror (BE)

Các checklist vận hành nằm ở FE repo: `astro-tarot-web-fe/docs/EXE201/`.

API hỗ trợ:

- `POST /api/v1/feedback` — khảo sát NPS (permitAll)
- `GET /api/v1/feedback/me/status` — đã gửi chưa + tổng / mục tiêu 20
- `POST /api/v1/marketing/events` — CTA / UTM (permitAll)
- `GET /api/v1/marketing/events/summary` — admin
- `GET /api/v1/admin/stats` → `traction` — khối Pitch

Migration: `V2_11__user_feedback_and_marketing.sql`
