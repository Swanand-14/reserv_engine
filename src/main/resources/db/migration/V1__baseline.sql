-- V1__baseline.sql
-- Baseline migration generated from the real dev DB schema
-- (tables were originally created by hand via mysql terminal)

CREATE TABLE `availability_window` (
                                       `id` char(36) NOT NULL,
                                       `owner_id` char(36) NOT NULL,
                                       `start_time` datetime(3) NOT NULL,
                                       `end_time` datetime(3) NOT NULL,
                                       `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                       PRIMARY KEY (`id`),
                                       CONSTRAINT `chk_window_time_order` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resource_pool` (
                                 `id` char(36) NOT NULL,
                                 `availability_window_id` char(36) NOT NULL,
                                 `owner_id` char(36) NOT NULL,
                                 `pool_mode` varchar(20) NOT NULL,
                                 `total_capacity` int NOT NULL,
                                 `remaining_capacity` int NOT NULL,
                                 `version` bigint NOT NULL DEFAULT '0',
                                 `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 PRIMARY KEY (`id`),
                                 KEY `idx_pool_window` (`availability_window_id`),
                                 CONSTRAINT `fk_pool_window` FOREIGN KEY (`availability_window_id`) REFERENCES `availability_window` (`id`),
                                 CONSTRAINT `chk_pool_capacity_bounds` CHECK (((`remaining_capacity` >= 0) and (`remaining_capacity` <= `total_capacity`))),
                                 CONSTRAINT `chk_pool_mode` CHECK ((`pool_mode` in (_utf8mb4'UNIT_BASED',_utf8mb4'COUNTER_BASED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resource_unit` (
                                 `id` char(36) NOT NULL,
                                 `resource_pool_id` char(36) NOT NULL,
                                 `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE',
                                 `version` bigint NOT NULL DEFAULT '0',
                                 `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 PRIMARY KEY (`id`),
                                 KEY `idx_unit_pool_status` (`resource_pool_id`,`status`),
                                 CONSTRAINT `fk_unit_pool` FOREIGN KEY (`resource_pool_id`) REFERENCES `resource_pool` (`id`),
                                 CONSTRAINT `chk_unit_status` CHECK ((`status` in (_utf8mb4'AVAILABLE',_utf8mb4'HELD',_utf8mb4'RESERVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `hold` (
                        `id` char(36) NOT NULL,
                        `holder_id` char(36) NOT NULL,
                        `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
                        `idempotency_key` varchar(100) NOT NULL,
                        `created_at` datetime(3) NOT NULL,
                        `expires_at` datetime(3) NOT NULL,
                        `version` bigint NOT NULL DEFAULT '0',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uq_hold_idempotency_key` (`idempotency_key`),
                        KEY `idx_hold_status_expiry` (`status`,`expires_at`),
                        CONSTRAINT `chk_hold_expiry_after_create` CHECK ((`expires_at` > `created_at`)),
                        CONSTRAINT `chk_hold_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'CONSUMED',_utf8mb4'EXPIRED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `hold_line` (
                             `id` char(36) NOT NULL,
                             `hold_id` char(36) NOT NULL,
                             `resource_pool_id` char(36) NOT NULL,
                             `resource_unit_id` char(36) DEFAULT NULL,
                             `quantity` int NOT NULL,
                             PRIMARY KEY (`id`),
                             KEY `fk_holdline_pool` (`resource_pool_id`),
                             KEY `idx_holdline_hold` (`hold_id`),
                             KEY `idx_holdline_unit` (`resource_unit_id`),
                             CONSTRAINT `fk_holdline_hold` FOREIGN KEY (`hold_id`) REFERENCES `hold` (`id`),
                             CONSTRAINT `fk_holdline_pool` FOREIGN KEY (`resource_pool_id`) REFERENCES `resource_pool` (`id`),
                             CONSTRAINT `fk_holdline_unit` FOREIGN KEY (`resource_unit_id`) REFERENCES `resource_unit` (`id`),
                             CONSTRAINT `chk_holdline_quantity_positive` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation` (
                               `id` varchar(36) NOT NULL,
                               `hold_id` varchar(36) NOT NULL,
                               `holder_id` varchar(36) NOT NULL,
                               `status` varchar(20) NOT NULL,
                               `confirmed_at` datetime NOT NULL,
                               `version` bigint NOT NULL,
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uq_reservation_hold_id` (`hold_id`),
                               CONSTRAINT `fk_reservation_hold` FOREIGN KEY (`hold_id`) REFERENCES `hold` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation_line` (
                                    `id` varchar(36) NOT NULL,
                                    `reservation_id` varchar(36) NOT NULL,
                                    `resource_pool_id` varchar(36) NOT NULL,
                                    `resource_unit_id` varchar(36) DEFAULT NULL,
                                    `quantity` int NOT NULL,
                                    `locked_price` decimal(12,2) NOT NULL,
                                    PRIMARY KEY (`id`),
                                    KEY `fk_reservation_line_pool` (`resource_pool_id`),
                                    KEY `fk_reservation_line_unit` (`resource_unit_id`),
                                    KEY `idx_reservation_line_reservation_id` (`reservation_id`),
                                    CONSTRAINT `fk_reservation_line_pool` FOREIGN KEY (`resource_pool_id`) REFERENCES `resource_pool` (`id`),
                                    CONSTRAINT `fk_reservation_line_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`),
                                    CONSTRAINT `fk_reservation_line_unit` FOREIGN KEY (`resource_unit_id`) REFERENCES `resource_unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_attempt` (
                                   `id` varchar(36) NOT NULL,
                                   `hold_id` varchar(36) NOT NULL,
                                   `idempotency_key` varchar(100) NOT NULL,
                                   `status` varchar(20) NOT NULL,
                                   `amount` decimal(12,2) NOT NULL,
                                   `created_at` datetime NOT NULL,
                                   `version` bigint NOT NULL,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uq_payment_attempt_idempotency_key` (`idempotency_key`),
                                   KEY `idx_payment_attempt_hold_id_status` (`hold_id`,`status`),
                                   CONSTRAINT `fk_payment_attempt_hold` FOREIGN KEY (`hold_id`) REFERENCES `hold` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
                         `id` char(36) NOT NULL,
                         `email` varchar(255) NOT NULL,
                         `password_hash` varchar(255) NOT NULL,
                         `created_at` datetime(3) NOT NULL,
                         `updated_at` datetime(3) NOT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uq_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_roles` (
                              `user_id` char(36) NOT NULL,
                              `role` varchar(20) NOT NULL,
                              PRIMARY KEY (`user_id`,`role`),
                              CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
                              CONSTRAINT `chk_user_roles_role` CHECK ((`role` in (_utf8mb4'PLATFORM_ADMIN',_utf8mb4'ORGANIZER',_utf8mb4'CUSTOMER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;