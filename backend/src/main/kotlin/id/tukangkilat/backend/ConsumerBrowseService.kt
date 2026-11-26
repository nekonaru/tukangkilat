package id.tukangkilat.backend

import id.tukangkilat.shared.TechnicianBrowseDto
import id.tukangkilat.shared.TechnicianVerificationStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/** Public, read-only directory of verified technicians for consumers. */
object ConsumerBrowseService {

    fun technicians(skill: String?, query: String?): List<TechnicianBrowseDto> = transaction {
        // LIKE wildcards from the caller would make the filter meaningless.
        if (listOfNotNull(skill, query).any { it.contains('%') || it.contains('_') }) {
            return@transaction emptyList()
        }
        val condition = with(SqlExpressionBuilder) {
            var base: Op<Boolean> = (UsersTable.role eq "TUKANG") and
                (UsersTable.status eq "AKTIF") and
                (TechnicianProfilesTable.verificationStatus eq TechnicianVerificationStatus.TERVERIFIKASI.name)
            if (!skill.isNullOrBlank()) {
                base = base and (TechnicianProfilesTable.skills like "%${skill.trim().uppercase()}%")
            }
            if (!query.isNullOrBlank()) {
                val q = "%${query.trim().lowercase()}%"
                base = base and (
                    (UsersTable.fullName.lowerCase() like q) or
                        (TechnicianProfilesTable.skills.lowerCase() like q)
                    )
            }
            base
        }
        UsersTable.innerJoin(TechnicianProfilesTable, { UsersTable.id }, { TechnicianProfilesTable.userId })
            .selectAll()
            .where { condition }
            .orderBy(TechnicianProfilesTable.ratingAvg to SortOrder.DESC)
            .limit(100)
            .map { row ->
                TechnicianBrowseDto(
                    id = row[UsersTable.id],
                    fullName = row[UsersTable.fullName],
                    skills = row[TechnicianProfilesTable.skills]
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    ratingAvg = row[TechnicianProfilesTable.ratingAvg],
                    jobsCount = row[TechnicianProfilesTable.jobsCount],
                    serviceRadiusKm = row[TechnicianProfilesTable.serviceRadiusKm],
                    isOnline = row[TechnicianProfilesTable.isOnline],
                    photoUrl = row[TechnicianProfilesTable.photoUrl]
                )
            }
    }
}
