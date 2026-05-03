CREATE DATABASE IF NOT EXISTS Prepandchiill;
USE Prepandchiill;

DROP TABLE IF EXISTS users;
-- 2. Core Tables
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    firebase_uid VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_users_firebase_uid (firebase_uid)
);
Select * From users;

DROP TABLE IF EXISTS subjects;
CREATE TABLE IF NOT EXISTS subjects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_subjects_name (name)
);
Select * From subjects;
ALTER TABLE subjects DROP COLUMN difficulty;

-- 3. Topics Table (To store your syllabus items)
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
Select * From topics;

-- 4. User Progress (The "Checkbox" Tracker)
DROP TABLE IF EXISTS user_topics;
CREATE TABLE IF NOT EXISTS user_topics (
    user_id INT NOT NULL,
    topic_id INT NOT NULL,
    is_completed BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (user_id, topic_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);
Select * From user_topics;
ALTER TABLE user_topics ADD COLUMN remaining_seconds INT DEFAULT NULL;

DROP TABLE IF EXISTS user_subjects;
-- 5. User_Subjects (For high-level tracking like exam dates/confidence)
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
Select * From user_subjects;



-- 6. Insert Subjects
INSERT IGNORE INTO subjects (name) VALUES 
('DBMS'), 
('Android Development');
ALTER TABLE subjects ADD COLUMN difficulty INT NOT NULL DEFAULT 2;

-- 7. Insert DBMS Syllabus Topics
INSERT INTO topics (subject_id, topic_name, hours_theory, hours_lab, co_mappings) VALUES
(1, 'Introduction and applications of DBMS, Purpose of data base, Data Independence, Database System architecture', 2, 2, 'CO1'),
(1, 'Database Design Process, ER Diagrams - Entities, Attributes, Relationships, Generalization, Specialization, Aggregation', 3, 3, 'CO1, CO2'),
(1, 'Relational model, constraints, Keys (Primary, Foreign, Super, Candidate), E-R to relational', 4, 4, 'CO1, CO2'),
(1, 'Schema refinement, functional dependencies, Normal forms (1NF-5NF)', 3, 3, 'CO2, CO4'),
(1, 'Basics of DQL, DDL, DML, DCL, Constraints, group by, having, order by', 4, 4, 'CO1, CO2'),
(1, 'Joins: Inner, Full, Outer, Self-join', 2, 2, 'CO1, CO2'),
(1, 'Views, Stored Procedures, Functions and Sub-queries', 2, 2, 'CO1, CO3'),
(1, 'Data Storage, Indexing and Hashing, B+ Tree, Clustered/Non-Clustered Index', 1, 2, 'CO1, CO3'),
(1, 'Transaction concepts, ACID, TCL commands', 2, 1, 'CO1, CO3'),
(1, 'Concurrency control, Lock Based Protocol, Deadlock, Timestamp, Validation', 2, 1, 'CO1, CO3');

-- 8. Insert Android Development Syllabus Topics
INSERT INTO topics (subject_id, topic_name, hours_theory, hours_lab, co_mappings) VALUES
(2, 'Android Foundation, Ecosystem, Versions, Architecture, DVM & ART, Android Studio, Gradle', 1, 1, 'CO1'),
(2, 'Activity Lifecycle, Creating an Activity, Resource Files', 1, 1, 'CO1'),
(2, 'Intents: Overview, Implicit/Explicit, Intent Filters, Passing Data', 2, 1, 'CO2, CO3'),
(2, 'UI Design: ConstraintLayout, Material Design, Views (RecyclerView, Dialogs, Menus)', 4, 4, 'CO2, CO3'),
(2, 'Device Hardware: Camera, Sensors (Accelerometer/Proximity), Audio/Video Playback', 2, 2, 'CO2, CO3'),
(2, 'Networking & APIs: REST API, JSON, Retrofit, Firebase Authentication', 2, 2, 'CO2, CO3'),
(2, 'Data Storage: Directory Structure, File Storage, SQLite (CRUD)', 2, 2, 'CO2, CO3'),
(2, 'Mobile Communication: SMS, Email, Notifications, Dialing', 1, 1, 'CO2, CO3'),
(2, 'Location-Based Services: Geocoding, Map-Based Activities', 1, 1, 'CO2, CO3');


UPDATE topics SET subject_id = (SELECT id FROM subjects WHERE name = 'DBMS' LIMIT 1) 
WHERE subject_id = 1;


UPDATE topics SET subject_id = (SELECT id FROM subjects WHERE name = 'Android Development' LIMIT 1) 
WHERE subject_id = 2;


select * from study_sessions;
CREATE TABLE study_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    subject_id INT,
    topic_id INT,
    minutes_spent INT,
    performance_score INT,  -- 0–100 (how well user understood)
    session_date DATE,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

select * from plan_history;
CREATE TABLE plan_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    subject_id INT,
    allocated_minutes INT,
    plan_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);





