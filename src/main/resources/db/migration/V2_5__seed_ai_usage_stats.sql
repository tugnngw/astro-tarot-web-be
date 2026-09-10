-- Seed vài bản ghi ai_usage_logs để biểu đồ token Tarot AI trên trang Quản trị
-- có số liệu khi môi trường chỉ chạy data demo (V2_4).
-- Idempotent theo id cố định.

INSERT INTO ai_usage_logs (
    id, user_id, reading_id, chat_session_id,
    provider, model, prompt_tokens, completion_tokens, total_tokens,
    estimated_cost_usd, latency_ms, created_at
)
SELECT * FROM (VALUES
    ('ac100000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     NULL::uuid,
     NULL::uuid,
     'Gemini', 'gemini-2.0-flash',
     1800, 620, 2420,
     0.000428::numeric, 1850, NOW() - INTERVAL '2 days'),

    ('ac100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     NULL::uuid,
     NULL::uuid,
     'Gemini', 'gemini-2.0-flash',
     2400, 410, 2810,
     0.000404::numeric, 980, NOW() - INTERVAL '2 days' + INTERVAL '2 minutes'),

    ('ac100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     NULL::uuid,
     NULL::uuid,
     'Gemini', 'gemini-2.0-flash',
     1500, 700, 2200,
     0.000430::numeric, 2100, NOW() - INTERVAL '5 days'),

    ('ac100000-0000-4000-8000-000000000004'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     NULL::uuid,
     NULL::uuid,
     'Gemini', 'gemini-1.5-flash',
     900, 350, 1250,
     0.000230::numeric, 1600, NOW() - INTERVAL '12 days'),

    ('ac100000-0000-4000-8000-000000000005'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     NULL::uuid,
     NULL::uuid,
     'Gemini', 'gemini-2.0-flash',
     3200, 890, 4090,
     0.000676::numeric, 2400, NOW() - INTERVAL '1 day')
) AS v(id, user_id, reading_id, chat_session_id,
       provider, model, prompt_tokens, completion_tokens, total_tokens,
       estimated_cost_usd, latency_ms, created_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM ai_usage_logs a WHERE a.id = v.id);
