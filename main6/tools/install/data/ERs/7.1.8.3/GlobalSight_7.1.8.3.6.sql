# For GBS-1499 : Support for Asia Online Machine Translation.
# 1. Add 5 new columns to 'tm_profile' table
ALTER TABLE `tm_profile` ADD COLUMN `AO_URL` VARCHAR(100) DEFAULT NULL;
ALTER TABLE `tm_profile` ADD COLUMN `AO_PORT` INTEGER DEFAULT -1;
ALTER TABLE `tm_profile` ADD COLUMN `AO_USERNAME` VARCHAR(100) DEFAULT NULL;
ALTER TABLE `tm_profile` ADD COLUMN `AO_PASSWORD` VARCHAR(100) DEFAULT NULL;
ALTER TABLE `tm_profile` ADD COLUMN `AO_ACCOUNT_NUMBER` INTEGER DEFAULT -1;

# 2. Create a new table to save "locale pair <-> domain combination" information.
CREATE TABLE `TM_PROFILE_AO_INFO` 
(
  ID BIGINT AUTO_INCREMENT PRIMARY KEY,
  TM_PROFILE_ID BIGINT,
  LANGUAGE_PAIR_CODE  BIGINT,
  LANGUAGE_PAIR_NAME VARCHAR(50),
  DOMAIN_COMBINATION_CODE  BIGINT,
  CONSTRAINT FK_TM_PROFILE_AO_INFO_TM_PROFILE_ID FOREIGN KEY (TM_PROFILE_ID) REFERENCES TM_PROFILE(ID)
) AUTO_INCREMENT = 1;