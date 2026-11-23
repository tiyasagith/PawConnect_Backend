package com.example.PawConnect.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup schema alignment to drop any obsolete unique constraints that prevent repeated checkouts,
 * ensuring seamless runtime compatibility across PostgreSQL and H2 without requiring manual DDL execution.
 */
@Slf4j
@Component
@Order(1)
public class DatabaseSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // Drop obsolete constraint if present so customers can place multiple consecutive orders without conflict
            jdbcTemplate.execute("ALTER TABLE IF EXISTS cart DROP CONSTRAINT IF EXISTS uk_cart_user_status");
            log.info("Database schema alignment: successfully verified/dropped legacy constraint 'uk_cart_user_status'");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice: {}", ex.getMessage());
        }

        try {
            // Drop any legacy enum check constraints on orders and history tables to allow OrderStatus.PENDING and PaymentStatus.PAID
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders DROP CONSTRAINT IF EXISTS orders_order_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders DROP CONSTRAINT IF EXISTS check_order_status");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders DROP CONSTRAINT IF EXISTS orders_payment_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders DROP CONSTRAINT IF EXISTS check_payment_status");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS order_status_history DROP CONSTRAINT IF EXISTS order_status_history_status_check");

            // Drop legacy enum check constraints on payments, payment_orders, appointments, and service_bookings
            // to allow PaymentStatus.PAID, CREATED, AppointmentStatus.IN_PROGRESS, BookingStatus.IN_PROGRESS
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payments DROP CONSTRAINT IF EXISTS payments_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payments DROP CONSTRAINT IF EXISTS check_status");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payments DROP CONSTRAINT IF EXISTS payments_payment_for_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payments DROP CONSTRAINT IF EXISTS check_payment_for");

            jdbcTemplate.execute("ALTER TABLE IF EXISTS payment_orders DROP CONSTRAINT IF EXISTS payment_orders_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payment_orders DROP CONSTRAINT IF EXISTS check_status");

            jdbcTemplate.execute("ALTER TABLE IF EXISTS appointments DROP CONSTRAINT IF EXISTS appointments_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS appointments DROP CONSTRAINT IF EXISTS check_status");

            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_bookings DROP CONSTRAINT IF EXISTS service_bookings_status_check");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_bookings DROP CONSTRAINT IF EXISTS check_status");

            log.info("Database schema alignment: successfully verified/dropped legacy order_status, payment_status, and booking check constraints");
        } catch (Exception ex) {
            log.warn("Database schema alignment notice on enum check constraints: {}", ex.getMessage());
        }

        try {
            // Synchronize legacy Payment rows into PaymentOrder if not already present
            jdbcTemplate.execute(
                "INSERT INTO payment_orders (razorpay_order_id, razorpay_payment_id, user_id, order_id, booking_id, booking_type, amount_in_paise, currency, status, signature_verified, created_at, updated_at) " +
                "SELECT p.gateway_order_id, p.gateway_payment_id, p.user_id, " +
                "CASE WHEN p.payment_for = 'ORDER' THEN p.reference_id ELSE NULL END, " +
                "CASE WHEN p.payment_for != 'ORDER' THEN p.reference_id ELSE NULL END, " +
                "CASE WHEN p.payment_for = 'APPOINTMENT' THEN 'VETERINARY' WHEN p.payment_for = 'SERVICE_BOOKING' THEN 'SERVICE_BOOKING' ELSE NULL END, " +
                "CAST(p.amount * 100 AS BIGINT), COALESCE(p.currency, 'INR'), p.status, TRUE, p.created_at, p.updated_at " +
                "FROM payments p " +
                "WHERE p.gateway_order_id IS NOT NULL " +
                "AND NOT EXISTS (SELECT 1 FROM payment_orders po WHERE po.razorpay_order_id = p.gateway_order_id)"
            );
            log.info("Database schema alignment: synced legacy payment records into payment_orders");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on payment sync: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS courier_partner VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS tracking_url VARCHAR(500)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS shipping_label_number VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS package_weight_kg NUMERIC(6, 2)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS package_dimensions VARCHAR(100)");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS packed_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS estimated_delivery_date DATE");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS dispatch_notes VARCHAR(1000)");
            log.info("Database schema alignment: verified/added logistics and shipping columns to orders table");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on orders logistics columns: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS order_items ADD COLUMN IF NOT EXISTS product_image_url VARCHAR(1000)");
            jdbcTemplate.execute(
                "UPDATE order_items oi SET product_image_url = (" +
                "SELECT pi.image_url FROM product_images pi " +
                "WHERE pi.product_id = oi.product_id " +
                "ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC LIMIT 1" +
                ") WHERE oi.product_image_url IS NULL AND oi.product_id IS NOT NULL"
            );
            log.info("Database schema alignment: verified order_items.product_image_url column and backfilled snapshots");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on order_items product_image_url: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS payment_orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS appointments ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_bookings ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP");
            log.info("Database schema alignment: verified payment_expires_at columns on orders, payment_orders, appointments, and service_bookings");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on payment_expires_at: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS promo_code VARCHAR(50)");
            log.info("Database schema alignment: verified promo_code column on orders");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on orders promo_code: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS broadcast_announcements (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "title VARCHAR(255) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "target_audience VARCHAR(100) NOT NULL, " +
                "dispatch_status VARCHAR(50) NOT NULL DEFAULT 'SENT', " +
                "recipient_count INT DEFAULT 0, " +
                "created_by_user_id BIGINT, " +
                "sent_at TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            log.info("Database schema alignment: verified/created broadcast_announcements table");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on broadcast_announcements: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN DEFAULT TRUE");
            log.info("Database schema alignment: verified users.email_notifications_enabled column");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on users email_notifications_enabled: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_provider_availability ADD COLUMN IF NOT EXISTS slot_duration_minutes INT DEFAULT 30");
            log.info("Database schema alignment: verified service_provider_availability.slot_duration_minutes column");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on service_provider_availability: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_provider_availability ADD COLUMN IF NOT EXISTS is_configured BOOLEAN DEFAULT FALSE");
            log.info("Database schema alignment: verified service_provider_availability.is_configured column");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on service_provider_availability.is_configured: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS services ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE");
            log.info("Database schema alignment: verified services.is_active column");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on services.is_active: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS veterinarian_availability ADD COLUMN IF NOT EXISTS is_configured BOOLEAN DEFAULT FALSE");
            log.info("Database schema alignment: verified veterinarian_availability.is_configured column");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on veterinarian_availability.is_configured: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS veterinarian_availability ALTER COLUMN start_time DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS veterinarian_availability ALTER COLUMN end_time DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS veterinarian_availability ALTER COLUMN slot_duration_minutes DROP NOT NULL");
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_vet_avail_vet_day ON veterinarian_availability (veterinarian_id, day_of_week)");
            log.info("Database schema alignment: relaxed not-null constraints on veterinarian_availability columns and ensured unique index");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on veterinarian_availability constraints: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_provider_availability ALTER COLUMN start_time DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_provider_availability ALTER COLUMN end_time DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS service_provider_availability ALTER COLUMN slot_duration_minutes DROP NOT NULL");
            log.info("Database schema alignment: relaxed not-null constraints on service_provider_availability columns");
        } catch (Exception ex) {
            log.debug("Database schema alignment notice on service_provider_availability constraints: {}", ex.getMessage());
        }

        try {
            // Ensure approved veterinarians have active clinical consultation availability configured for working days (Monday-Saturday)
            for (String day : new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"}) {
                jdbcTemplate.execute(
                    "INSERT INTO veterinarian_availability (veterinarian_id, day_of_week, start_time, end_time, slot_duration_minutes, is_available, is_configured, created_at) " +
                    "SELECT v.id, '" + day + "', '09:00:00'::time, '18:00:00'::time, 30, true, true, CURRENT_TIMESTAMP " +
                    "FROM veterinarians v " +
                    "WHERE v.verification_status = 'APPROVED' AND v.is_active = true " +
                    "ON CONFLICT (veterinarian_id, day_of_week) DO UPDATE " +
                    "SET is_available = true, is_configured = true, " +
                    "start_time = COALESCE(veterinarian_availability.start_time, '09:00:00'::time), " +
                    "end_time = COALESCE(veterinarian_availability.end_time, '18:00:00'::time), " +
                    "slot_duration_minutes = COALESCE(veterinarian_availability.slot_duration_minutes, 30)"
                );
            }
            log.info("Database schema alignment: verified and initialized active weekly availability for approved veterinarians");
        } catch (Exception ex) {
            log.warn("Database schema alignment notice on veterinarian availability initialization: {}", ex.getMessage());
        }

        // Phase 9: Safe one-time legacy demo image cleanup with backup and explicit seed URL allowlist
        // Preserves all genuine user uploads, historical order snapshots, and skips on subsequent boots
        executeSafeSeedImageCleanup();
    }

    private void executeSafeSeedImageCleanup() {
        try {
            // 1. Create guard table for one-time execution tracking
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS schema_migration_guards (" +
                "guard_key VARCHAR(100) PRIMARY KEY, " +
                "executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "details VARCHAR(255)" +
                ")"
            );

            // 2. Check if cleanup has already been performed
            Integer alreadyExecuted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migration_guards WHERE guard_key = 'PHASE9_SEED_IMAGE_ALLOWLIST_CLEANUP'",
                Integer.class
            );

            if (alreadyExecuted != null && alreadyExecuted > 0) {
                log.info("Phase 9 Image Safety: Seed cleanup already executed previously. Skipping startup cleanup.");
                return;
            }

            // 3. Create persistent backup table before making any changes
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS legacy_image_backup (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "source_table VARCHAR(50) NOT NULL, " +
                "record_id BIGINT, " +
                "column_name VARCHAR(50) NOT NULL, " +
                "original_url TEXT NOT NULL, " +
                "backed_up_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 4. Targeted allowlist of known legacy demo seed image signatures
            String[] knownSeeds = {
                "photo-1534528741775-53994a69daeb",
                "photo-1559839734-2b71ea197ec2",
                "photo-1622253692010-333f2da6031d",
                "photo-1580489944761-15a19d654956",
                "photo-1507003211169-0a1dd7228f2d",
                "photo-1500648767791-00dcc994a43e",
                "photo-1494790108377-be9c29b29330",
                "photo-1573496359142-b8d87734a5a2",
                "photo-1584820927498-cfe5211fd8bf",
                "photo-1576201836106-db1758fd1c97",
                "photo-1516734212186-a967f81ad0d7",
                "photo-1583511655857-d19b40a7a54e",
                "photo-1541599540903-216a46ca1dc0",
                "photo-1587300003388-59208cc962cb",
                "photo-1548199973-03cce0bbc87b",
                "photo-1537151625747-768eb6cf92b2",
                "photo-1552053831-71594a27632d",
                "photo-1514888286974-6c03e2ca1dba",
                "photo-1573865526739-10659fec78a5",
                "photo-1589924691995-400dc9ecc119",
                "photo-1568640347023-a616a30bc3bd",
                "photo-1583337130417-3346a1be7dee"
            };

            for (String seed : knownSeeds) {
                String pattern = "%" + seed + "%";

                // Back up and clean users
                jdbcTemplate.update(
                    "INSERT INTO legacy_image_backup (source_table, record_id, column_name, original_url) " +
                    "SELECT 'users', id, 'profile_image', profile_image FROM users WHERE profile_image LIKE ?",
                    pattern
                );
                jdbcTemplate.update("UPDATE users SET profile_image = NULL WHERE profile_image LIKE ?", pattern);

                // Back up and clean pets
                jdbcTemplate.update(
                    "INSERT INTO legacy_image_backup (source_table, record_id, column_name, original_url) " +
                    "SELECT 'pets', id, 'photo_url', photo_url FROM pets WHERE photo_url LIKE ?",
                    pattern
                );
                jdbcTemplate.update("UPDATE pets SET photo_url = NULL WHERE photo_url LIKE ?", pattern);

                // Back up and clean services
                jdbcTemplate.update(
                    "INSERT INTO legacy_image_backup (source_table, record_id, column_name, original_url) " +
                    "SELECT 'services', id, 'image_url', image_url FROM services WHERE image_url LIKE ?",
                    pattern
                );
                jdbcTemplate.update("UPDATE services SET image_url = NULL WHERE image_url LIKE ?", pattern);

                // Back up and clean product_images
                jdbcTemplate.update(
                    "INSERT INTO legacy_image_backup (source_table, record_id, column_name, original_url) " +
                    "SELECT 'product_images', id, 'image_url', image_url FROM product_images WHERE image_url LIKE ?",
                    pattern
                );
                jdbcTemplate.update("DELETE FROM product_images WHERE image_url LIKE ?", pattern);
            }

            // 5. Record completion guard so this does not run again on startup
            jdbcTemplate.update(
                "INSERT INTO schema_migration_guards (guard_key, details) VALUES ('PHASE9_SEED_IMAGE_ALLOWLIST_CLEANUP', 'Safe seed allowlist cleanup with backup executed')"
            );

            log.info("Phase 9 Image Safety: Successfully completed one-time allowlist demo image cleanup and backup.");
        } catch (Exception ex) {
            log.warn("Phase 9 Image Safety: Notice during seed image allowlist cleanup: {}", ex.getMessage());
        }
    }
}
