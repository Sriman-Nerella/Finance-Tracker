package com.example.financetracker.data.repo

import com.example.financetracker.data.db.CategoryDao
import com.example.financetracker.data.db.toDomain
import com.example.financetracker.data.db.toSeedEntity
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.DefaultCategories
import com.example.financetracker.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Room-backed [CategoryRepository]. */
class OfflineCategoryRepository(
    private val dao: CategoryDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    override suspend fun get(id: String): Category? =
        dao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    /** Seeded rows are not dirty and use updatedAt 0, so existing (local or cloud) rows are never overwritten. */
    override suspend fun seedDefaults() {
        dao.insertIgnore(DefaultCategories.all.map { it.toSeedEntity() })
    }
}
