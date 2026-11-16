ALTER TABLE routines ALTER COLUMN days_of_week TYPE VARCHAR(80);

INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'FOOD', '음식', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'FOOD');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'EMOTION', '감정', 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'EMOTION');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'PEOPLE', '사람', 30, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'PEOPLE');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'PLACE', '장소', 40, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'PLACE');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'EMERGENCY', '긴급어', 50, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'EMERGENCY');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'SOCIAL', '인사·사회어', 60, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'SOCIAL');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'TIME', '시간', 70, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'TIME');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'ENDING', '어미', 80, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'ENDING');
INSERT INTO symbol_categories (code, name, display_order, active, created_at, updated_at)
SELECT 'BODY', '신체', 90, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM symbol_categories WHERE code = 'BODY');

INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '물', NULL, '물 주세요', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'FOOD' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '물');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '밥', NULL, '밥 먹고 싶어요', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'FOOD' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '밥');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '좋아요', NULL, '좋아요', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMOTION' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '좋아요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '싫어요', NULL, '싫어요', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMOTION' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '싫어요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '불안해요', NULL, '불안해요', FALSE, 30, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMOTION' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '불안해요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '엄마', NULL, '엄마', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'PEOPLE' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '엄마');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '선생님', NULL, '선생님', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'PEOPLE' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '선생님');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '화장실', NULL, '화장실에 가고 싶어요', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'PLACE' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '화장실');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '집', NULL, '집에 가고 싶어요', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'PLACE' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '집');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '도와주세요', NULL, '도와주세요', TRUE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMERGENCY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '도와주세요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '아파요', NULL, '아파요', TRUE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMERGENCY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '아파요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '시끄러워요', NULL, '너무 시끄러워요', TRUE, 30, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMERGENCY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '시끄러워요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '쉬고 싶어요', NULL, '잠깐 쉬고 싶어요', TRUE, 40, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'EMERGENCY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '쉬고 싶어요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '네', NULL, '네', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'SOCIAL' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '네');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '아니요', NULL, '아니요', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'SOCIAL' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '아니요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '잠깐만요', NULL, '잠깐만요', FALSE, 30, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'SOCIAL' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '잠깐만요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '몰라요', NULL, '잘 모르겠어요', FALSE, 40, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'SOCIAL' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '몰라요');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '머리', NULL, '머리가 아파요', FALSE, 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'BODY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '머리');
INSERT INTO symbols (category_id, name, image_url, tts_text, emergency, display_order, active, created_at, updated_at)
SELECT c.id, '배', NULL, '배가 아파요', FALSE, 20, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM symbol_categories c
WHERE c.code = 'BODY' AND NOT EXISTS (SELECT 1 FROM symbols s WHERE s.category_id = c.id AND s.name = '배');
