-- The ai_analysis_histories.analysis_type column had a stale CHECK constraint
-- (CK__ai_analys__analy__015F0FBB) only allowing 'SINGLE_KEYWORD' or 'TOP_TRENDS'.
-- It predates the COLLECTION_ANALYSIS value added to AiAnalysisType, and Hibernate's
-- ddl-auto: update does not manage CHECK constraints, so every real call to
-- POST /api/v1/ai/analyze-collection/{id} silently failed to persist its history
-- record (swallowed by AiAnalysisHistoryServiceImpl's non-fatal try/catch).
-- Run this manually against any environment (staging/prod/teammate DBs) that still
-- has the old constraint.

IF EXISTS (
    SELECT 1 FROM sys.check_constraints cc
    JOIN sys.tables t ON cc.parent_object_id = t.object_id
    WHERE t.name = 'ai_analysis_histories' AND cc.name = 'CK__ai_analys__analy__015F0FBB'
)
BEGIN
    ALTER TABLE ai_analysis_histories DROP CONSTRAINT CK__ai_analys__analy__015F0FBB;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints cc
    JOIN sys.tables t ON cc.parent_object_id = t.object_id
    WHERE t.name = 'ai_analysis_histories' AND cc.name = 'CK_ai_analysis_histories_analysis_type'
)
BEGIN
    ALTER TABLE ai_analysis_histories ADD CONSTRAINT CK_ai_analysis_histories_analysis_type
        CHECK (analysis_type IN ('SINGLE_KEYWORD', 'TOP_TRENDS', 'COLLECTION_ANALYSIS'));
END
