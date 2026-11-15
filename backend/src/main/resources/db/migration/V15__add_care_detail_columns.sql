ALTER TABLE care_recipients ADD COLUMN date_of_birth DATE;
ALTER TABLE care_recipients ADD COLUMN allergies VARCHAR(500);
ALTER TABLE care_recipients ADD COLUMN conditions VARCHAR(500);
ALTER TABLE care_recipients ADD COLUMN emergency_contact_name VARCHAR(120);
ALTER TABLE care_recipients ADD COLUMN emergency_contact_phone VARCHAR(30);
