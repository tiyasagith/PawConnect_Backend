-- Services inherit their type from service_providers.provider_type.
ALTER TABLE services DROP COLUMN IF EXISTS category_id;
