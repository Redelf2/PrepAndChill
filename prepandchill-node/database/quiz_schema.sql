-- Quiz proficiency system — run after subjects/topics exist.
USE Prepandchiill;

CREATE TABLE IF NOT EXISTS quiz_questions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    topic_id INT NOT NULL,
    question TEXT NOT NULL,
    option_a VARCHAR(768) NOT NULL,
    option_b VARCHAR(768) NOT NULL,
    option_c VARCHAR(768) NOT NULL,
    option_d VARCHAR(768) NOT NULL,
    correct_option ENUM('A', 'B', 'C', 'D') NOT NULL,
    difficulty_level TINYINT NOT NULL DEFAULT 2,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    KEY idx_topic_difficulty (topic_id, difficulty_level)
);

CREATE TABLE IF NOT EXISTS user_quiz_attempts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    question_id INT NOT NULL,
    topic_id INT NOT NULL,
    subject_id INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    difficulty_level TINYINT NOT NULL,
    answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    KEY idx_user_subject_time (user_id, subject_id, answered_at DESC),
    KEY idx_user_question (user_id, question_id)
);

CREATE TABLE IF NOT EXISTS user_quiz_stats (
    user_id INT NOT NULL,
    subject_id INT NOT NULL,
    quiz_sessions INT UNSIGNED NOT NULL DEFAULT 0,
    streak_correct_sessions INT NOT NULL DEFAULT 0,
    streak_fail_sessions INT NOT NULL DEFAULT 0,
    prior_weighted_accuracy DECIMAL(5, 4) NULL,
    last_confidence TINYINT UNSIGNED NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, subject_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);

/*
-- SAMPLE SEED (optional): ensure at least one matching topic exists.
INSERT INTO quiz_questions (
    topic_id, question,
    option_a, option_b, option_c, option_d,
    correct_option, difficulty_level
)
SELECT
    t.id,
    'Which SQL statement retrieves rows?',
    'SELECT',
    'FETCH',
    'GET',
    'PULL ROWS',
    'A',
    1
FROM topics t
INNER JOIN subjects s ON s.id = t.subject_id
WHERE LOWER(s.name) = 'dbms'
LIMIT 1;
*/
