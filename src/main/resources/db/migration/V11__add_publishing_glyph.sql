-- V11: publishing 테이블에 글리프 배지 필드 추가
ALTER TABLE publishing
    ADD COLUMN glyph_text VARCHAR(3) NULL COMMENT '카드 좌상단 글리프 텍스트 (1~3자)',
    ADD COLUMN glyph_background_color VARCHAR(30) NULL COMMENT '글리프 배경 색상 키 (예: mint, lavender, peach)';
