CREATE DATABASE IF NOT EXISTS Prepandchiill;
USE Prepandchiill;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    firebase_uid VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_users_firebase_uid (firebase_uid)
);

-- 2. Subjects Table
CREATE TABLE IF NOT EXISTS subjects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    difficulty INT NOT NULL DEFAULT 2,
    UNIQUE KEY uq_subjects_name (name)
);

-- 3. Topics Table (Syllabus items)
DROP TABLE IF EXISTS topics;
CREATE TABLE topics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    topic_name TEXT NOT NULL,
    hours_theory INT,
    hours_lab INT,
    co_mappings VARCHAR(50),
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);

-- 4. User Topics (Checkbox Tracker)
CREATE TABLE IF NOT EXISTS user_topics (
    user_id INT NOT NULL,
    topic_id INT NOT NULL,
    is_completed BOOLEAN DEFAULT FALSE,
    remaining_seconds INT DEFAULT NULL,
    PRIMARY KEY (user_id, topic_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

-- 5. User Subjects (High-level tracking)
DROP TABLE IF EXISTS user_subjects;
CREATE TABLE IF NOT EXISTS user_subjects (
    user_id INT NOT NULL,
    subject_id INT NOT NULL,
    exam_date DATE NULL,
    confidence INT NULL,
    difficulty INT NOT NULL DEFAULT 2,
    PRIMARY KEY (user_id, subject_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);

-- 6. Study Sessions
CREATE TABLE IF NOT EXISTS study_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    subject_id INT,
    topic_id INT,
    minutes_spent INT,
    performance_score INT,  
    session_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

-- 7. Plan History
CREATE TABLE IF NOT EXISTS plan_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    subject_id INT,
    allocated_minutes INT,
    plan_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);

-- ==========================================
-- VIEWS
-- ==========================================

-- 8. User Dashboard View (Used in subjectRoutes.js)
CREATE OR REPLACE VIEW user_dashboard_view AS
SELECT 
    u.firebase_uid, 
    s.id as subject_id, 
    s.name, 
    us.exam_date, 
    us.confidence
FROM user_subjects us
JOIN subjects s ON s.id = us.subject_id
JOIN users u ON u.id = us.user_id;

-- ==========================================
-- INITIAL DATA SEEDING
-- ==========================================

INSERT IGNORE INTO subjects (name) VALUES ('DBMS'), ('Android Development');

-- DBMS Topics
INSERT INTO topics (subject_id, topic_name, hours_theory, hours_lab, co_mappings) VALUES
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Introduction and applications of DBMS...', 2, 2, 'CO1'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Database Design Process, ER Diagrams...', 3, 3, 'CO1, CO2'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Relational model, constraints, Keys...', 4, 4, 'CO1, CO2'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Schema refinement, functional dependencies...', 3, 3, 'CO2, CO4'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Basics of DQL, DDL, DML, DCL, Constraints...', 4, 4, 'CO1, CO2'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Joins: Inner, Full, Outer, Self-join', 2, 2, 'CO1, CO2'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Views, Stored Procedures, Functions and Sub-queries', 2, 2, 'CO1, CO3'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Data Storage, Indexing and Hashing...', 1, 2, 'CO1, CO3'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Transaction concepts, ACID, TCL commands', 2, 1, 'CO1, CO3'),
((SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1), 'Concurrency control, Lock Based Protocol...', 2, 1, 'CO1, CO3');

-- Android Topics
INSERT INTO topics (subject_id, topic_name, hours_theory, hours_lab, co_mappings) VALUES
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Android Foundation...', 1, 1, 'CO1'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Activity Lifecycle...', 1, 1, 'CO1'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Intents: Overview...', 2, 1, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'UI Design: ConstraintLayout...', 4, 4, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Device Hardware...', 2, 2, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Networking & APIs...', 2, 2, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Data Storage: Directory Structure...', 2, 2, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Mobile Communication...', 1, 1, 'CO2, CO3'),
((SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1), 'Location-Based Services...', 1, 1, 'CO2, CO3');
