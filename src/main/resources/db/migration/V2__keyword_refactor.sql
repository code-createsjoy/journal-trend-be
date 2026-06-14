-- Drop old tables if they exist
DROP TABLE IF EXISTS follow_topics;
DROP TABLE IF EXISTS paper_topics;
DROP TABLE IF EXISTS topic_trends;
DROP TABLE IF EXISTS topics;

-- Alter papers to remove open_alex_id column and add sourceType/sourceIdentifier
ALTER TABLE papers DROP COLUMN IF EXISTS open_alex_id;
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('papers') AND name = 'source_type')
BEGIN
    ALTER TABLE papers ADD source_type VARCHAR(50);
END
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('papers') AND name = 'source_identifier')
BEGIN
    ALTER TABLE papers ADD source_identifier VARCHAR(100);
END

-- Alter authors to remove open_alex_id column and add sourceType/sourceIdentifier
ALTER TABLE authors DROP COLUMN IF EXISTS open_alex_id;
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('authors') AND name = 'source_type')
BEGIN
    ALTER TABLE authors ADD source_type VARCHAR(50);
END
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('authors') AND name = 'source_identifier')
BEGIN
    ALTER TABLE authors ADD source_identifier VARCHAR(100);
END

-- Alter notifications to drop topic_id and add keyword_id
ALTER TABLE notifications DROP COLUMN IF EXISTS topic_id;
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('notifications') AND name = 'keyword_id')
BEGIN
    ALTER TABLE notifications ADD keyword_id BIGINT;
END

-- Create Keywords table
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('keywords') AND type = 'U')
BEGIN
    CREATE TABLE keywords (
        keyword_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        term NVARCHAR(255) UNIQUE NOT NULL,
        domain NVARCHAR(255) NOT NULL,
        paper_count INT DEFAULT 0,
        trend_score DECIMAL(18, 2) DEFAULT 0.00,
        created_at DATETIME2 DEFAULT GETDATE()
    );
END

-- Create PaperKeyword many-to-many relationship table
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('paper_keywords') AND type = 'U')
BEGIN
    CREATE TABLE paper_keywords (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        paper_id BIGINT FOREIGN KEY REFERENCES papers(id) ON DELETE CASCADE,
        keyword_id BIGINT FOREIGN KEY REFERENCES keywords(keyword_id) ON DELETE CASCADE,
        CONSTRAINT UQ_Paper_Keyword UNIQUE (paper_id, keyword_id)
    );
END

-- Create PublicationTrend table
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('publication_trends') AND type = 'U')
BEGIN
    CREATE TABLE publication_trends (
        trend_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        keyword_id BIGINT FOREIGN KEY REFERENCES keywords(keyword_id) ON DELETE CASCADE,
        trend_year INT NOT NULL,
        trend_month INT NOT NULL,
        paper_count INT DEFAULT 0,
        delta_percent DECIMAL(18, 2) DEFAULT 0.00,
        created_at DATETIME2 DEFAULT GETDATE()
    );
END

-- Create FollowKeyword table
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('follow_keywords') AND type = 'U')
BEGIN
    CREATE TABLE follow_keywords (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT FOREIGN KEY REFERENCES users(id) ON DELETE CASCADE,
        keyword_id BIGINT FOREIGN KEY REFERENCES keywords(keyword_id) ON DELETE CASCADE,
        followed_at DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT UQ_User_Keyword UNIQUE (user_id, keyword_id)
    );
END

-- Add constraint for notifications.keyword_id if not exists
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_notifications_keywords' AND parent_object_id = OBJECT_ID('notifications'))
BEGIN
    ALTER TABLE notifications ADD CONSTRAINT FK_notifications_keywords FOREIGN KEY (keyword_id) REFERENCES keywords(keyword_id) ON DELETE SET NULL;
END

-- Create indexes
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_keywords_term' AND object_id = OBJECT_ID('keywords'))
BEGIN
    CREATE INDEX idx_keywords_term ON keywords(term);
END

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_keywords_domain' AND object_id = OBJECT_ID('keywords'))
BEGIN
    CREATE INDEX idx_keywords_domain ON keywords(domain);
END

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_paper_keywords_keyword_id' AND object_id = OBJECT_ID('paper_keywords'))
BEGIN
    CREATE INDEX idx_paper_keywords_keyword_id ON paper_keywords(keyword_id);
END

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_paper_keywords_paper_id' AND object_id = OBJECT_ID('paper_keywords'))
BEGIN
    CREATE INDEX idx_paper_keywords_paper_id ON paper_keywords(paper_id);
END

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_publication_trends_lookup' AND object_id = OBJECT_ID('publication_trends'))
BEGIN
    CREATE INDEX idx_publication_trends_lookup ON publication_trends(keyword_id, trend_year, trend_month);
END
