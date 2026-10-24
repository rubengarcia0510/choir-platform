ALTER TABLE concerts
DROP CONSTRAINT ck_concert_status;

ALTER TABLE concerts
ADD CONSTRAINT ck_concert_status
CHECK (status IN (
    'PLANNED',
    'REHEARSAL',
    'PUBLISHED',
    'COMPLETED',
    'CANCELLED',
    'ARCHIVED'
));
