CREATE TABLE USER
(
   ID BIGINT
      AUTO_INCREMENT
      PRIMARY KEY,
   USER_ID VARCHAR(300),
   STATE INTEGER,
   USER_NAME VARCHAR(100),
   FIRST_NAME VARCHAR(100),
   LAST_NAME VARCHAR(100),
   TITLE VARCHAR(100),
   COMPANY_NAME VARCHAR(40),
   PASSWORD VARCHAR(1000),
   EMAIL VARCHAR(1000),
   CC_EMAIL VARCHAR(1000),
   BCC_EMAIL VARCHAR(1000),
   ADDRESS VARCHAR(1000),
   DEFAULT_LOCALE VARCHAR(100),
   TYPE INTEGER,
   OFFICE_PHONE_NUMBER VARCHAR(300),
   HOME_PHONE_NUMBER VARCHAR(300),
   CELL_PHONE_NUMBER VARCHAR(300),
   FAX_PHONE_NUMBER VARCHAR(300),
   
   IN_ALL_PROJECTS CHAR(1)
     NOT NULL
     CHECK (IS_ACTIVE IN ('Y', 'N'))
) AUTO_INCREMENT = 1000;

CREATE INDEX IDX_USER_JOB_USER_ID ON USER(USER_ID);
CREATE INDEX IDX_USER_JOB_USER_NAME ON USER(USER_NAME);

CREATE TABLE CONTAINER_ROLE
(
  id BIGINT AUTO_INCREMENT
      PRIMARY KEY,
  NAME VARCHAR(100),
  STATE BIGINT,
  ACTIVITY_ID BIGINT,
  SOURCE_LOCALE VARCHAR(100),
  TARGET_LOCALE VARCHAR(100)
) AUTO_INCREMENT = 1000;

CREATE INDEX IDX_CONTAINER_ROLE_NAME ON CONTAINER_ROLE(NAME);

CREATE TABLE USER_ROLE
(
  id BIGINT AUTO_INCREMENT
      PRIMARY KEY,
  NAME VARCHAR(100),
  STATE BIGINT,
  ACTIVITY_ID BIGINT,
  SOURCE_LOCALE VARCHAR(100),
  TARGET_LOCALE VARCHAR(100),
  USER VARCHAR(100),
  RATE VARCHAR(100),
  COST VARCHAR(100)
) AUTO_INCREMENT = 1000;

CREATE INDEX IDX_USER_ROLE_NAME ON USER_ROLE(NAME);

CREATE TABLE CONTAINER_ROLE_USER_IDS
(
  ROLE_ID BIGINT,
  USER_ID VARCHAR(100),
  PRIMARY KEY (ROLE_ID, USER_ID)
) AUTO_INCREMENT = 1000;

CREATE TABLE CONTAINER_ROLE_RATE
(
  ROLE_ID BIGINT,
  RATE_ID BIGINT,
  PRIMARY KEY (ROLE_ID, RATE_ID)
) AUTO_INCREMENT = 1000;