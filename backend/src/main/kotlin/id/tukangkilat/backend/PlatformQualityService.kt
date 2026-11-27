package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.OffsetDateTime

/** v5 platform quality: offline sync, key simulation, limits, versions, widgets and jobs. */
object PlatformQualityService {

    fun queue(userId: Long, req: QueueSyncRequest): SyncOperationDto = transaction {
        if (req.entity.isBlank() || req.operation !in listOf("CREATE", "UPDATE", "DELETE")) {
            throw BadRequestException("Operasi tidak valid")
        }
        if (req.payload.length > 8192) throw BadRequestException("Payload terlalu besar")
        val id = SyncOperationsTable.insert {
            it[this.userId] = userId
            it[entity] = req.entity.trim().take(64)
            it[entityId] = req.entityId.trim().take(64)
            it[operation] = req.operation
            it[payload] = req.payload
            it[version] = req.version
            it[status] = "MENUNGGU"
            it[conflict] = false
            it[createdAt] = OffsetDateTime.now()
        } get SyncOperationsTable.id
        operations(userId).first { it.id == id }
    }

    fun operations(userId: Long): List<SyncOperationDto> = transaction {
        SyncOperationsTable.selectAll().where { SyncOperationsTable.userId eq userId }
            .orderBy(SyncOperationsTable.createdAt to SortOrder.DESC).limit(100).map {
                SyncOperationDto(
                    it[SyncOperationsTable.id], it[SyncOperationsTable.entity], it[SyncOperationsTable.entityId],
                    it[SyncOperationsTable.operation], it[SyncOperationsTable.version], it[SyncOperationsTable.status],
                    it[SyncOperationsTable.conflict], it[SyncOperationsTable.createdAt].toString()
                )
            }
    }

    /** Simple server-wins conflict rule: older versions are marked as conflicts. */
    fun processSync(userId: Long): SyncResultDto = transaction {
        val pending = SyncOperationsTable.selectAll().where {
            (SyncOperationsTable.userId eq userId) and (SyncOperationsTable.status eq "MENUNGGU")
        }.toList()
        var conflicts = 0
        pending.forEach { row ->
            val latestVersion = SyncOperationsTable.selectAll().where {
                (SyncOperationsTable.userId eq userId) and
                    (SyncOperationsTable.entity eq row[SyncOperationsTable.entity]) and
                    (SyncOperationsTable.entityId eq row[SyncOperationsTable.entityId])
            }.maxOfOrNull { it[SyncOperationsTable.version] } ?: 0
            val isConflict = row[SyncOperationsTable.version] < latestVersion
            if (isConflict) conflicts++
            SyncOperationsTable.update({ SyncOperationsTable.id eq row[SyncOperationsTable.id] }) {
                it[status] = if (isConflict) "KONFLIK" else "TERSINKRON"
                it[conflict] = isConflict
                it[syncedAt] = OffsetDateTime.now()
            }
        }
        SyncResultDto(pending.size, conflicts)
    }

    fun chatKeys(userId: Long): List<ChatKeyDto> = transaction {
        ChatKeysTable.selectAll().where { ChatKeysTable.userId eq userId }
            .orderBy(ChatKeysTable.createdAt to SortOrder.DESC).map {
                ChatKeyDto(it[ChatKeysTable.id], it[ChatKeysTable.publicKey], it[ChatKeysTable.fingerprint],
                    it[ChatKeysTable.active], it[ChatKeysTable.createdAt].toString())
            }
    }

    fun generateKey(userId: Long, publicKey: String): ChatKeyDto = transaction {
        if (publicKey.isBlank() || publicKey.length > 512) throw BadRequestException("Kunci tidak valid")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)
        ChatKeysTable.update({ ChatKeysTable.userId eq userId }) { it[active] = false }
        val id = ChatKeysTable.insert {
            it[this.userId] = userId
            it[this.publicKey] = publicKey.trim()
            it[this.fingerprint] = fingerprint
            it[active] = true
            it[createdAt] = OffsetDateTime.now()
        } get ChatKeysTable.id
        chatKeys(userId).first { it.id == id }
    }

    fun rateLimits(): List<RateLimitDto> = transaction {
        RateLimitsTable.selectAll().orderBy(RateLimitsTable.code to SortOrder.ASC).map {
            RateLimitDto(it[RateLimitsTable.code], it[RateLimitsTable.maxCalls],
                it[RateLimitsTable.windowSeconds], it[RateLimitsTable.enabled])
        }
    }

    fun saveRateLimit(code: String, req: SaveRateLimitRequest): RateLimitDto = transaction {
        if (req.maxCalls !in 1..10000 || req.windowSeconds !in 1..86400) throw BadRequestException("Batas tidak valid")
        val exists = RateLimitsTable.selectAll().where { RateLimitsTable.code eq code }.any()
        if (exists) {
            RateLimitsTable.update({ RateLimitsTable.code eq code }) {
                it[maxCalls] = req.maxCalls
                it[windowSeconds] = req.windowSeconds
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            RateLimitsTable.insert {
                it[this.code] = code.trim().take(64)
                it[maxCalls] = req.maxCalls
                it[windowSeconds] = req.windowSeconds
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        rateLimits().first { it.code == code.trim().take(64) }
    }

    fun apiVersions(): List<ApiVersionDto> = transaction {
        if (!ApiVersionsTable.selectAll().any()) {
            ApiVersionsTable.insert {
                it[version] = "v1"
                it[status] = "AKTIF"
                it[note] = "API utama"
                it[releasedAt] = OffsetDateTime.now()
            }
        }
        ApiVersionsTable.selectAll().orderBy(ApiVersionsTable.version to SortOrder.ASC).map {
            ApiVersionDto(it[ApiVersionsTable.version], it[ApiVersionsTable.status], it[ApiVersionsTable.note],
                it[ApiVersionsTable.releasedAt].toString())
        }
    }

    fun saveApiVersion(version: String, req: SaveApiVersionRequest): ApiVersionDto = transaction {
        if (req.status !in listOf("AKTIF", "DEPREKATED", "NONAKTIF")) throw BadRequestException("Status tidak valid")
        val exists = ApiVersionsTable.selectAll().where { ApiVersionsTable.version eq version }.any()
        if (exists) {
            ApiVersionsTable.update({ ApiVersionsTable.version eq version }) {
                it[status] = req.status
                it[note] = req.note.take(255)
            }
        } else {
            ApiVersionsTable.insert {
                it[this.version] = version.trim().take(16)
                it[status] = req.status
                it[note] = req.note.take(255)
                it[releasedAt] = OffsetDateTime.now()
            }
        }
        apiVersions().first { it.version == version.trim().take(16) }
    }

    fun widgets(userId: Long): List<WidgetConfigDto> = transaction {
        WidgetConfigsTable.selectAll().where { WidgetConfigsTable.userId eq userId }
            .orderBy(WidgetConfigsTable.position to SortOrder.ASC).map {
                WidgetConfigDto(it[WidgetConfigsTable.widgetType], it[WidgetConfigsTable.enabled], it[WidgetConfigsTable.position])
            }
    }

    fun saveWidget(userId: Long, req: SaveWidgetRequest) = transaction {
        if (req.widgetType.isBlank() || req.position < 0) throw BadRequestException("Widget tidak valid")
        val exists = WidgetConfigsTable.selectAll().where {
            (WidgetConfigsTable.userId eq userId) and (WidgetConfigsTable.widgetType eq req.widgetType)
        }.any()
        if (exists) {
            WidgetConfigsTable.update({
                (WidgetConfigsTable.userId eq userId) and (WidgetConfigsTable.widgetType eq req.widgetType)
            }) {
                it[enabled] = req.enabled
                it[position] = req.position
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            WidgetConfigsTable.insert {
                it[this.userId] = userId
                it[widgetType] = req.widgetType.trim().take(48)
                it[enabled] = req.enabled
                it[position] = req.position
                it[updatedAt] = OffsetDateTime.now()
            }
        }
    }

    fun jobs(): List<ScheduledJobDto> = transaction {
        if (!ScheduledJobsTable.selectAll().any()) {
            listOf("DISPATCH" to "Dispatch", "MAINTENANCE" to "Maintenance", "AUTO_ONLINE" to "Auto online").forEach { (code, name) ->
                ScheduledJobsTable.insert {
                    it[this.code] = code
                    it[this.name] = name
                    it[intervalMs] = 60000L
                    it[enabled] = true
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
        ScheduledJobsTable.selectAll().orderBy(ScheduledJobsTable.code to SortOrder.ASC).map {
            ScheduledJobDto(it[ScheduledJobsTable.id], it[ScheduledJobsTable.code], it[ScheduledJobsTable.name],
                it[ScheduledJobsTable.intervalMs], it[ScheduledJobsTable.enabled], it[ScheduledJobsTable.lastRun]?.toString())
        }
    }

    fun saveJob(req: SaveJobRequest): ScheduledJobDto = transaction {
        if (req.intervalMs !in 1000..86_400_000) throw BadRequestException("Interval tidak valid")
        val exists = ScheduledJobsTable.selectAll().where { ScheduledJobsTable.code eq req.code }.any()
        if (exists) {
            ScheduledJobsTable.update({ ScheduledJobsTable.code eq req.code }) {
                it[name] = req.name.take(120)
                it[intervalMs] = req.intervalMs
                it[enabled] = req.enabled
            }
        } else {
            ScheduledJobsTable.insert {
                it[this.code] = req.code.trim().take(64)
                it[name] = req.name.trim().take(120)
                it[intervalMs] = req.intervalMs
                it[enabled] = req.enabled
                it[createdAt] = OffsetDateTime.now()
            }
        }
        jobs().first { it.code == req.code.trim().take(64) }
    }

    fun runJob(code: String): ScheduledJobDto = transaction {
        val row = ScheduledJobsTable.selectAll().where { ScheduledJobsTable.code eq code }
            .singleOrNull() ?: throw NotFoundException("Jadwal tidak ditemukan")
        ScheduledJobsTable.update({ ScheduledJobsTable.id eq row[ScheduledJobsTable.id] }) {
            it[lastRun] = OffsetDateTime.now()
        }
        jobs().first { it.id == row[ScheduledJobsTable.id] }
    }
}
