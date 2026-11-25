package id.tukangkilat.app.data.repository

import id.tukangkilat.app.data.local.FavoriteDao
import id.tukangkilat.app.data.local.FavoriteTechnician
import id.tukangkilat.app.data.local.toFavorite
import id.tukangkilat.shared.TechnicianPublicDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val dao: FavoriteDao
) {
    private val _items = MutableStateFlow<List<FavoriteTechnician>>(emptyList())
    val items: StateFlow<List<FavoriteTechnician>> = _items.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { _items.value = dao.all() }

    suspend fun isFavorite(userId: Long): Boolean = withContext(Dispatchers.IO) {
        userId in dao.ids()
    }

    suspend fun toggle(technician: TechnicianPublicDto) = withContext(Dispatchers.IO) {
        if (technician.id in dao.ids()) dao.remove(technician.id) else dao.upsert(technician.toFavorite())
        _items.value = dao.all()
    }

    suspend fun remove(userId: Long) = withContext(Dispatchers.IO) {
        dao.remove(userId)
        _items.value = dao.all()
    }
}
