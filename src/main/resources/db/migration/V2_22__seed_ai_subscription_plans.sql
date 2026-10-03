-- V2_22: Seed default AI subscription plans for Tarot AI
-- 5 active plans across DAY_PASS and MONTHLY tiers

INSERT INTO subscription_plan (id, plan_type, name, daily_quota, price, duration_days, is_active, description)
VALUES
    (
        'a0000000-0000-4000-8000-000000000002',
        'DAY_PASS',
        'Basic 1 Ngày',
        5,
        15000,
        1,
        TRUE,
        'Gói trải nghiệm 1 ngày: 5 lượt AI/ngày'
    ),
    (
        'a0000000-0000-4000-8000-000000000003',
        'DAY_PASS',
        'Combo 3 Ngày',
        10,
        39000,
        3,
        TRUE,
        'Gói tiết kiệm 3 ngày: 10 lượt AI/ngày'
    ),
    (
        'a0000000-0000-4000-8000-000000000004',
        'DAY_PASS',
        'VIP 7 Ngày',
        15,
        79000,
        7,
        TRUE,
        'Gói tuần năng động: 15 lượt AI/ngày'
    ),
    (
        'a0000000-0000-4000-8000-000000000005',
        'MONTHLY',
        'Cơ bản Tháng',
        10,
        99000,
        30,
        TRUE,
        'Gói tháng cơ bản: 10 lượt AI/ngày'
    ),
    (
        'a0000000-0000-4000-8000-000000000006',
        'MONTHLY',
        'Pro Tháng',
        25,
        199000,
        30,
        TRUE,
        'Gói tháng cao cấp: 25 lượt AI/ngày không giới hạn tính năng'
    )
ON CONFLICT (id) DO NOTHING;
