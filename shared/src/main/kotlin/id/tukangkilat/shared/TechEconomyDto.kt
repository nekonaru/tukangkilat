package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class ReputationDto(val quality: Int, val response: Int, val punctuality: Int, val satisfaction: Int, val overall: Int)

@Serializable
data class CertificationDto(val id: Long, val code: String, val title: String, val expiresAt: String, val status: String, val score: Int)

@Serializable
data class CompleteCertRequest(val score: Int)

@Serializable
data class CourseDto(val code: String, val title: String, val minutes: Int, val rewardPoints: Int)

@Serializable
data class EnrollmentDto(val id: Long, val courseCode: String, val progress: Int, val completed: Boolean)

@Serializable
data class GearDto(val id: Long, val name: String, val pricePoints: Int, val owned: Boolean)

@Serializable
data class TeamDto(val id: Long, val leadId: Long, val name: String)

@Serializable
data class CreateTeamRequest(val name: String)

@Serializable
data class HelperDto(val id: Long, val teamId: Long, val helperId: Long, val status: String)

@Serializable
data class AddHelperRequest(val teamId: Long, val helperId: Long)

@Serializable
data class HeatmapDto(val dayOfWeek: Int, val hour: Int, val weight: Int)

@Serializable
data class SaveHeatmapRequest(val dayOfWeek: Int, val hour: Int, val weight: Int)

@Serializable
data class WorkPermitDto(val id: Long, val orderId: Long, val permitCode: String, val issuedAt: String, val closedAt: String?)

@Serializable
data class ForecastDto(val id: Long, val period: String, val expected: Long)

@Serializable
data class TaxDto(val id: Long, val gross: Long, val taxPercent: Int, val taxAmount: Long, val net: Long)

@Serializable
data class SimulateTaxRequest(val gross: Long, val taxPercent: Int)

@Serializable
data class PayoutScheduleDto(val frequency: String, val minAmount: Long, val enabled: Boolean)

@Serializable
data class SavePayoutScheduleRequest(val frequency: String, val minAmount: Long, val enabled: Boolean)

@Serializable
data class QualityDto(val id: Long, val orderId: Long, val score: Int, val note: String)

@Serializable
data class SaveQualityRequest(val orderId: Long, val score: Int, val note: String)

@Serializable
data class StorefrontDto(val headline: String, val description: String, val customPrice: Long)

@Serializable
data class SaveStorefrontRequest(val headline: String, val description: String, val customPrice: Long)
