/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.calendar.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomMasterTable.TABLE_NAME
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.android.calendar.data.api.MailSettingsEntity
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_CALENDARS
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_EVENTS
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_EVENTS_METADATA
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_EVENTS_OCCURRENCES
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_EVENT_ALARMS
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_FETCHED_EVENTS_METADATA
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_MANAGED_HOLIDAY_CALENDARS
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_MEMBERS
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.auth.data.db.AuthDatabase
import me.proton.core.challenge.data.db.ChallengeDatabase
import me.proton.core.contact.data.local.db.ContactDatabase
import me.proton.core.data.room.db.extension.addTableColumn
import me.proton.core.data.room.db.extension.dropTable
import me.proton.core.data.room.db.extension.dropTableColumn
import me.proton.core.data.room.db.extension.recreateTable
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.keytransparency.data.local.KeyTransparencyDatabase
import me.proton.core.mailsettings.data.db.MailSettingsDatabase
import me.proton.core.notification.data.local.db.NotificationDatabase
import me.proton.core.observability.data.db.ObservabilityDatabase
import me.proton.core.payment.data.local.db.PaymentDatabase
import me.proton.core.push.data.local.db.PushDatabase
import me.proton.core.telemetry.data.db.TelemetryDatabase
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.db.UserKeyDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity
import me.proton.core.userrecovery.data.db.DeviceRecoveryDatabase
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsDatabase

object AppDatabaseMigrations {

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(AppDatabase.TABLE_ADDRESSES, "displayName", "TEXT")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(AppDatabase.TABLE_EVENTS, "sharedEventId", "TEXT")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(AppDatabase.TABLE_EVENTS, "isProtonProtonInvite", "INTEGER")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(AppDatabase.TABLE_CALENDARS, "type", "INTEGER NOT NULL" , "0")

            db.execSQL("CREATE TABLE IF NOT EXISTS ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId TEXT NOT NULL PRIMARY KEY, createTime INTEGER NOT NULL, lastUpdateTime INTEGER NOT NULL, status INTEGER NOT NULL, url TEXT NOT NULL, FOREIGN KEY (calendarId) REFERENCES calendars (id) ON DELETE CASCADE ON UPDATE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_subscriptions_calendarId ON ${AppDatabase.TABLE_CALENDAR_SUBSCRIPTIONS} (calendarId)")
        }
    }

    /**
     * Copy Core DB into Calendar DB.
     */
    fun MIGRATION_28_29(context: Context) = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create all needed tables before attaching old Core DB.
            AccountDatabase.MIGRATION_0.migrate(db)
            AccountDatabase.MIGRATION_1.migrate(db)
            AccountDatabase.MIGRATION_2.migrate(db)
            AccountDatabase.MIGRATION_3.migrate(db)
            UserDatabase.MIGRATION_0.migrate(db)
            AddressDatabase.MIGRATION_0.migrate(db)
            AddressDatabase.MIGRATION_1.migrate(db)
            KeySaltDatabase.MIGRATION_0.migrate(db)
            HumanVerificationDatabase.MIGRATION_0.migrate(db)
            PublicAddressDatabase.MIGRATION_0.migrate(db)
            MailSettingsDatabase.MIGRATION_0.migrate(db)

            // End current transaction (from FrameworkSQLiteOpenHelper.onUpgrade(db, version, mNewVersion))
            db.setTransactionSuccessful()
            db.endTransaction()
            // Attach old Core DB to current DB (cannot be done within a transaction).
            val coreDbFile = context.getDatabasePath("db-account-manager")
            val coreDbPath = coreDbFile.path
            db.execSQL("ATTACH DATABASE '$coreDbPath' AS coreDb")
            // Begin transaction for attached migration.
            db.beginTransaction()

            // Import all data from Core DB to current DB.
            listOf(
                AccountEntity::class.simpleName,
                AccountMetadataEntity::class.simpleName,
                AddressEntity::class.simpleName,
                AddressKeyEntity::class.simpleName,
                HumanVerificationEntity::class.simpleName,
                MailSettingsEntity::class.simpleName,
                PublicAddressEntity::class.simpleName,
                PublicAddressKeyEntity::class.simpleName,
                KeySaltEntity::class.simpleName,
                SessionDetailsEntity::class.simpleName,
                SessionEntity::class.simpleName,
                UserEntity::class.simpleName,
                UserKeyEntity::class.simpleName
            ).forEach { table ->
                runCatching { db.execSQL("INSERT INTO main.$table SELECT * FROM coreDb.$table") }
            }

            // End current transaction to detach coreDb.
            db.setTransactionSuccessful()
            db.endTransaction()
            db.execSQL("DETACH DATABASE coreDb")
            // Begin transaction as it should be for a migration/onUpgrade.
            db.beginTransaction()

            // Recreate tables with proper foreign key, while keeping data.
            db.recreateTable(
                table = AppDatabase.TABLE_CALENDARS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_CALENDARS}` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `color` TEXT NOT NULL, `display` INTEGER NOT NULL, `flags` INTEGER NOT NULL, `type` INTEGER NOT NULL, `fkUserId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_calendars_fkUserId` ON `${AppDatabase.TABLE_CALENDARS}` (`fkUserId`)") },
            )
            db.recreateTable(
                table = AppDatabase.TABLE_CALENDAR_USER_SETTINGS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_CALENDAR_USER_SETTINGS}` (`fkUserId` TEXT NOT NULL, `weekLength` INTEGER NOT NULL, `displayWeekNumber` INTEGER NOT NULL, `autoDetectPrimaryTimezone` INTEGER NOT NULL, `primaryTimezone` TEXT NOT NULL, `displaySecondaryTimezone` INTEGER NOT NULL, `secondaryTimezone` TEXT, `viewPreference` INTEGER NOT NULL, `defaultCalendarId` TEXT, PRIMARY KEY(`fkUserId`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_user_settings_fkUserId` ON `${AppDatabase.TABLE_CALENDAR_USER_SETTINGS}` (`fkUserId`)") },
            )

            db.recreateTable(
                table = AppDatabase.TABLE_USER_SETTINGS,
                createTable = { execSQL("CREATE TABLE IF NOT EXISTS `${AppDatabase.TABLE_USER_SETTINGS}` (`fkUserId` TEXT NOT NULL, `weekStart` INTEGER NOT NULL, `dateFormat` INTEGER NOT NULL, `timeFormat` INTEGER NOT NULL, PRIMARY KEY(`fkUserId`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )") },
                createIndices = { execSQL("CREATE INDEX IF NOT EXISTS `index_user_settings_fkUserId` ON `${AppDatabase.TABLE_USER_SETTINGS}` (`fkUserId`)") },
            )

            // Drop old Calendar tables.
            db.dropTable(AppDatabase.TABLE_USERS)
            db.dropTable(AppDatabase.TABLE_ADDRESSES)

            // Delete Core Database.
            coreDbFile.delete()
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop unused public_keys table.
            db.dropTable(AppDatabase.TABLE_PUBLIC_KEYS)
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Two new entities/migrations in core.
            UserSettingsDatabase.MIGRATION_0.migrate(db)
            OrganizationDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Two new migrations in core.
            AddressDatabase.MIGRATION_2.migrate(db)
            PublicAddressDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // One new migration in core.
            ContactDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AccountDatabase.MIGRATION_4.migrate(db)
            AddressDatabase.MIGRATION_3.migrate(db)
            UserDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_34_35 = object: Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // One new migration in core.
            EventMetadataDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            OrganizationDatabase.MIGRATION_1.migrate(db)
            FeatureFlagDatabase.MIGRATION_0.migrate(db)
            FeatureFlagDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ChallengeDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ChallengeDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // create temporary table with new schema
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_EVENTS + "_temp"}` (`id` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `sharedEventId` TEXT, `calendarKeyPacket` TEXT, `createTime` INTEGER NOT NULL, `modifyTime` INTEGER NOT NULL, `permissions` INTEGER NOT NULL, `addressKeyPacket` TEXT, `addressId` TEXT, `sharedKeyPacket` TEXT, `sharedEvents` TEXT NOT NULL, `calendarEvents` TEXT NOT NULL, `personalEvents` TEXT NOT NULL, `attendeesEvents` TEXT NOT NULL, `attendees` TEXT NOT NULL, `isProtonProtonInvite` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`calendarId`) REFERENCES `calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            // copy data from old to new, without non-yet-existing columns
            db.execSQL("INSERT INTO `${TABLE_EVENTS + "_temp"}`(id, calendarId, sharedEventId, calendarKeyPacket, createTime, modifyTime, permissions, sharedKeyPacket, sharedEvents, calendarEvents, personalEvents, attendeesEvents, attendees, isProtonProtonInvite) SELECT id, calendarId, sharedEventId, calendarKeyPacket, createTime, modifyTime, permissions, sharedKeyPacket, sharedEvents, calendarEvents, personalEvents, attendeesEvents, attendees, isProtonProtonInvite FROM `${TABLE_EVENTS}`")

            // drop old table
            db.execSQL("DROP TABLE `${TABLE_EVENTS}`")

            // rename temporary table to old name
            db.execSQL("ALTER TABLE `${TABLE_EVENTS + "_temp"}` RENAME TO `${TABLE_EVENTS}`")

            // recreate index that we had on original table
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_calendarId` ON `${TABLE_EVENTS}` (`calendarId`)")

        }
    }

    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(AppDatabase.TABLE_CALENDAR_USER_SETTINGS, "autoImportInvite", "INTEGER NOT NULL" , "0")
        }
    }

    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // 1. add new columns to Member
            db.addTableColumn(
                table = TABLE_MEMBERS,
                column = "color",
                type = "TEXT NOT NULL",
                defaultValue = "#8080FF" // purple_base as of 20.06.2022
            )
            db.addTableColumn(
                table = TABLE_MEMBERS,
                column = "display",
                type = "INTEGER NOT NULL",
                defaultValue = "0"
            )
            db.addTableColumn(
                table = TABLE_MEMBERS,
                column = "flags",
                type = "INTEGER NOT NULL",
                defaultValue = "1" // legacy default was 1 if not present
            )

            // 2. copy the values from Calendar to Member
            db.query("SELECT id, color, display, flags FROM $TABLE_CALENDARS").let { cursor ->
                while (cursor.moveToNext()) {
                    val contentValues = ContentValues().apply {
                        put("color", cursor.getString(1))
                        put("display", cursor.getInt(2))
                        put("flags", cursor.getInt(3))
                    }
                    db.update(TABLE_MEMBERS, SQLiteDatabase.CONFLICT_IGNORE, contentValues, "calendarId = \"${cursor.getString(0)}\"", null)
                }
            }

            // 3. create temp Calendar table with new schema and copy values
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_CALENDARS + "_temp"}` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `type` INTEGER NOT NULL, `fkUserId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("INSERT INTO `${TABLE_CALENDARS + "_temp"}`(id, name, description, type, fkUserId) SELECT id, name, description, type, fkUserId FROM `${TABLE_CALENDARS}`")

            // 4. drop old Calendar table
            db.execSQL("DROP TABLE `${TABLE_CALENDARS}`")

            // 5. rename temp Calendar to new Calendar table
            db.execSQL("ALTER TABLE `${TABLE_CALENDARS + "_temp"}` RENAME TO `${TABLE_CALENDARS}`")

            // 6. recreate index
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendars_fkUserId` ON `${TABLE_CALENDARS}` (`fkUserId`)")

        }
    }

    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FeatureFlagDatabase.MIGRATION_2.migrate(db)
            FeatureFlagDatabase.MIGRATION_3.migrate(db)
            HumanVerificationDatabase.MIGRATION_1.migrate(db)
            HumanVerificationDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // delete all existing EMAIL notifications because we don't
            db.execSQL("DELETE FROM `${TABLE_EVENT_ALARMS}` WHERE `action` = 1")
        }
    }

    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PaymentDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(db: SupportSQLiteDatabase) {

            db.addTableColumn(AppDatabase.TABLE_EVENTS, "isPersonalMigrated", "INTEGER")
            db.addTableColumn(AppDatabase.TABLE_EVENTS, "notifications", "TEXT")

        }
    }

    val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // 1. add new columns to Member
            db.addTableColumn(
                table = TABLE_MEMBERS,
                column = "name",
                type = "TEXT NOT NULL",
                defaultValue = ""
            )
            db.addTableColumn(
                table = TABLE_MEMBERS,
                column = "description",
                type = "TEXT NOT NULL",
                defaultValue = ""
            )

            // 2. copy the values from Calendar to Member
            db.query("SELECT id, name, description FROM $TABLE_CALENDARS").let { cursor ->
                while (cursor.moveToNext()) {
                    val contentValues = ContentValues().apply {
                        put("name", cursor.getString(1))
                        put("description", cursor.getString(2))
                    }
                    db.update(TABLE_MEMBERS, SQLiteDatabase.CONFLICT_IGNORE, contentValues, "calendarId = \"${cursor.getString(0)}\"", null)
                }
            }

            // 3. create temp Calendar table with new schema and copy values
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_CALENDARS + "_temp"}` (`id` TEXT NOT NULL, `type` INTEGER NOT NULL, `fkUserId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("INSERT INTO `${TABLE_CALENDARS + "_temp"}`(id, type, fkUserId) SELECT id, type, fkUserId FROM `${TABLE_CALENDARS}`")

            // 4. drop old Calendar table
            db.execSQL("DROP TABLE `${TABLE_CALENDARS}`")

            // 5. rename temp Calendar to new Calendar table
            db.execSQL("ALTER TABLE `${TABLE_CALENDARS + "_temp"}` RENAME TO `${TABLE_CALENDARS}`")

            // 6. recreate index
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendars_fkUserId` ON `${TABLE_CALENDARS}` (`fkUserId`)")

        }
    }

    val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AccountDatabase.MIGRATION_5.migrate(db)
        }
    }

    val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ObservabilityDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            OrganizationDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(TABLE_MEMBERS, "addressId", "TEXT")
        }
    }

    val MIGRATION_51_52 = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AddressDatabase.MIGRATION_4.migrate(db)
            PublicAddressDatabase.MIGRATION_2.migrate(db)
            KeyTransparencyDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_52_53 = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_MANAGED_HOLIDAY_CALENDARS}` (`calendarId` TEXT NOT NULL, `country` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `languageCode` TEXT NOT NULL, `language` TEXT NOT NULL, `timezones` TEXT NOT NULL, `passphrase` TEXT NOT NULL, `sessionKey` TEXT NOT NULL, `fkUserId` TEXT NOT NULL, PRIMARY KEY(`calendarId`), FOREIGN KEY(`fkUserId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_managed_holiday_calendars_fkUserId` ON `${TABLE_MANAGED_HOLIDAY_CALENDARS}` (`fkUserId`)")
        }
    }

    val MIGRATION_53_54 = object : Migration(53, 54) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_54_55 = object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            NotificationDatabase.MIGRATION_0.migrate(db)
            NotificationDatabase.MIGRATION_1.migrate(db)
            PushDatabase.MIGRATION_0.migrate(db)
        }
    }

    val MIGRATION_55_56 = object : Migration(55, 56) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_56_57 = object : Migration(56, 57) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(TABLE_CALENDARS, "owner", "TEXT")
            db.addTableColumn(TABLE_MEMBERS, "priority", "INTEGER")
        }
    }

    val MIGRATION_57_58 = object : Migration(57, 58) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ContactDatabase.MIGRATION_1.migrate(db)
            EventMetadataDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_58_59 = object : Migration(58, 59) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(TABLE_MANAGED_HOLIDAY_CALENDARS, "hidden", "INTEGER")
        }
    }

    val MIGRATION_59_60 = object : Migration(59, 60) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserDatabase.MIGRATION_3.migrate(db)
            AccountDatabase.MIGRATION_6.migrate(db)
        }
    }

    val MIGRATION_60_61 = object : Migration(60, 61) {
        override fun migrate(db: SupportSQLiteDatabase) {
            TelemetryDatabase.MIGRATION_0.migrate(db)
            UserSettingsDatabase.MIGRATION_3.migrate(db)
        }
    }

    val MIGRATION_61_62 = object : Migration(61, 62) {
        override fun migrate(db: SupportSQLiteDatabase) {
            EventMetadataDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_62_63 = object : Migration(62, 63) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_4.migrate(db)
        }
    }

    val MIGRATION_63_64 = object : Migration(63, 64) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_5.migrate(db)
            UserKeyDatabase.MIGRATION_0.migrate(db)
            UserDatabase.MIGRATION_4.migrate(db)
            UserDatabase.MIGRATION_5.migrate(db)
            AccountDatabase.MIGRATION_7.migrate(db)
        }
    }

    val MIGRATION_64_65 = object : Migration(64, 65) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PaymentDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_65_66 = object : Migration(65, 66) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(TABLE_EVENTS, "color", "TEXT")
        }
    }

    val MIGRATION_66_67 = object : Migration(66, 67) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_EVENTS_METADATA}` (`id` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `sharedEventId` TEXT NOT NULL, `addressId` TEXT, `startTime` INTEGER NOT NULL, `startTimeZone` TEXT NOT NULL, `endTime` INTEGER NOT NULL, `endTimeZone` TEXT NOT NULL, `fullDay` INTEGER NOT NULL, `uid` TEXT NOT NULL, `recurrenceID` INTEGER, `exDates` TEXT NOT NULL, `rRule` TEXT, `createTime` INTEGER NOT NULL, `modifyTime` INTEGER NOT NULL, `isOrganizer` INTEGER NOT NULL, `sharedKeyPacket` TEXT, `calendarKeyPacket` TEXT, `addressKeyPacket` TEXT, `isPersonalSingleEdit` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`calendarId`) REFERENCES `calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_metadata_calendarId` ON `${TABLE_EVENTS_METADATA}` (`calendarId`)")
        }
    }

    val MIGRATION_67_68 = object : Migration(67, 68) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_6.migrate(db)
            DeviceRecoveryDatabase.MIGRATION_0.migrate(db)
            DeviceRecoveryDatabase.MIGRATION_1.migrate(db)
            UserKeyDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_68_69 = object : Migration(68, 69) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop `personalEvents` and `isPersonalMigrated` from `events`
            // create temporary table with new schema
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_EVENTS + "_temp"}` (`id` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `sharedEventId` TEXT, `calendarKeyPacket` TEXT, `createTime` INTEGER NOT NULL, `modifyTime` INTEGER NOT NULL, `permissions` INTEGER NOT NULL, `addressKeyPacket` TEXT, `addressId` TEXT, `sharedKeyPacket` TEXT, `sharedEvents` TEXT NOT NULL, `calendarEvents` TEXT NOT NULL, `attendeesEvents` TEXT NOT NULL, `attendees` TEXT NOT NULL, `isProtonProtonInvite` INTEGER, `notifications` TEXT, `color` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`calendarId`) REFERENCES `calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            // copy data from old to new, without non-yet-existing columns
            db.execSQL("INSERT INTO `${TABLE_EVENTS + "_temp"}`(id, calendarId, sharedEventId, calendarKeyPacket, createTime, modifyTime, permissions, addressKeyPacket, addressId, sharedKeyPacket, sharedEvents, calendarEvents, attendeesEvents, attendees, isProtonProtonInvite, notifications, color) SELECT id, calendarId, sharedEventId, calendarKeyPacket, createTime, modifyTime, permissions, addressKeyPacket, addressId, sharedKeyPacket, sharedEvents, calendarEvents, attendeesEvents, attendees, isProtonProtonInvite, notifications, color FROM `${TABLE_EVENTS}`")

            // drop old table
            db.execSQL("DROP TABLE `${TABLE_EVENTS}`")

            // rename temporary table to old name
            db.execSQL("ALTER TABLE `${TABLE_EVENTS + "_temp"}` RENAME TO `${TABLE_EVENTS}`")

            // recreate index that we had on original table
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_calendarId` ON `${TABLE_EVENTS}` (`calendarId`)")
        }
    }

    val MIGRATION_69_70 = object : Migration(69, 70) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AccountDatabase.MIGRATION_8.migrate(db)
            UserSettingsDatabase.MIGRATION_7.migrate(db)
            PublicAddressDatabase.MIGRATION_3.migrate(db)
            EventMetadataDatabase.MIGRATION_3.migrate(db)
        }
    }

    val MIGRATION_70_71 = object : Migration(70, 71) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ContactDatabase.MIGRATION_2.migrate(db)
        }
    }

    val MIGRATION_71_72 = object : Migration(71, 72) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AuthDatabase.MIGRATION_0.migrate(db)
            AuthDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_72_73 = object : Migration(72, 73) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AuthDatabase.MIGRATION_2.migrate(db)
            AuthDatabase.MIGRATION_3.migrate(db)
            AuthDatabase.MIGRATION_4.migrate(db)
            AuthDatabase.MIGRATION_5.migrate(db)
            UserDatabase.MIGRATION_6.migrate(db)
            AccountDatabase.MIGRATION_9.migrate(db)
            MailSettingsDatabase.MIGRATION_1.migrate(db)
        }
    }

    val MIGRATION_73_74 = object : Migration(73, 74) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_EVENTS_OCCURRENCES}` (`userId` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `eventUid` TEXT NOT NULL, `fullDay` INTEGER NOT NULL, `startTime` INTEGER, `endTime` INTEGER, `windowStartTime` INTEGER NOT NULL, `windowEndTime` INTEGER NOT NULL, `firstOccurrenceStartTime` INTEGER NOT NULL, `lastOccurrenceEndTime` INTEGER, `rRule` TEXT, `modifyTime` INTEGER NOT NULL, `_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_occurrences_eventId` ON `${TABLE_EVENTS_OCCURRENCES}` (`eventId`)")
        }
    }

    val MIGRATION_74_75 = object : Migration(74, 75) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_FETCHED_EVENTS_METADATA}` (`userId` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `windowStartTime` INTEGER NOT NULL, `windowEndTime` INTEGER NOT NULL, `_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`calendarId`) REFERENCES `calendars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fetched_events_metadata_calendarId` ON `${TABLE_FETCHED_EVENTS_METADATA}` (`calendarId`)")
        }
    }

    val MIGRATION_75_76 = object : Migration(75, 76) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MailSettingsDatabase.MIGRATION_2.migrate(db)
            MailSettingsDatabase.MIGRATION_3.migrate(db)
        }
    }

    val MIGRATION_76_77 = object : Migration(76, 77) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UserSettingsDatabase.MIGRATION_8.migrate(db)
            AccountDatabase.MIGRATION_10.migrate(db)
        }
    }

    val MIGRATION_77_78 = object : Migration(77, 78) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(TABLE_EVENTS, "attendeesInfo", "TEXT")
        }
    }

    val MIGRATION_78_79 = object : Migration(78, 79) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTableColumn(
                table = TABLE_FETCHED_EVENTS_METADATA,
                column = "validUntilMs",
                type = "INTEGER NOT NULL",
                defaultValue = "0",
            )
            FeatureFlagDatabase.MIGRATION_4.migrate(db)
        }
    }

    val MIGRATION_79_80 = object : Migration(79, 80) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AccountDatabase.MIGRATION_11.migrate(db)
        }
    }
}
