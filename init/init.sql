-- 容器内 mysql 客户端默认连接字符集是 latin1,必须显式切到 utf8mb4,否则中文会双重编码乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS springboot_db DEFAULT CHARACTER SET utf8mb4;
USE springboot_db;

create table if not exists course
(
    id             bigint auto_increment
        primary key,
    name           varchar(100)                            not null,
    teacher_name   varchar(50)                             null,
    credit         decimal(3, 1) default 0.0               null comment '学分（支持1.5这种小数）',
    capacity       int           default 0                 null comment '容量（默认0，代表不可选）',
    selected_count int           default 0                 null comment '已选人数（冗余字段）',
    start_time     varchar(20)                             null,
    end_time       varchar(20)                             null,
    classroom      varchar(50)                             null,
    semester       varchar(20)                             null,
    is_open        tinyint       default 1                 null,
    open_time      datetime                                null,
    create_time    datetime      default CURRENT_TIMESTAMP null,
    update_time    datetime      default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted        tinyint       default 0                 null
);

create table if not exists course_selection
(
    id          bigint auto_increment
        primary key,
    student_id  bigint                             not null,
    course_id   bigint                             not null,
    status      tinyint  default 0                 null comment '0-正常，1-已退课',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted     tinyint  default 0                 null,
    constraint uk_student_course
        unique (student_id, course_id)
);

create table if not exists course_waiting_queue
(
    id           bigint auto_increment
        primary key,
    student_id   bigint                             not null,
    course_id    bigint                             not null,
    queue_number int      default 0                 null comment '排队序号',
    status       tinyint  default 0                 null comment '0-排队中,1-已转正,2-已取消',
    create_time  datetime default CURRENT_TIMESTAMP null,
    update_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted      tinyint  default 0                 null,
    constraint uk_student_course
        unique (student_id, course_id)
);

create table if not exists student
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                             not null comment '姓名',
    student_no  varchar(20)                             not null comment '学号',
    max_credit  decimal(4, 1) default 30.0              null comment '学分上限',
    password    varchar(255)  default ''                not null,
    create_time datetime      default CURRENT_TIMESTAMP null,
    update_time datetime      default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted     tinyint       default 0                 null,
    role        tinyint       default 0                 not null comment '0学生 1管理员',
    constraint uk_student_no_deleted
        unique (student_no, deleted)
);

-- 插入管理员账号（密码是 BCrypt 加密后的值，你需要预先用项目里的加密工具生成）
INSERT INTO student (name, student_no, max_credit, password, role)
VALUES ('管理员', 'admin', 0, '$2a$10$oa6OYXyXumZoVnPGwR1ENuxb576lqJugv7DwwQpLYDMhO2Cc9hm2m', 1);

-- 插入一门测试课程
INSERT INTO course (name, teacher_name, credit, capacity, selected_count, start_time, end_time, is_open)
VALUES ('Java入门', '王老师', 3.0, 1, 0, '2026-09-01 08:00:00', '2026-09-01 10:00:00', 1);