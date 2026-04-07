package com.dave_cli.proxybox.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingRuleDao {

    @Query("SELECT * FROM routing_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedRule(): RoutingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(rule: RoutingRuleEntity)

    @Delete
    suspend fun delete(rule: RoutingRuleEntity)

    @Query("UPDATE routing_rules SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE routing_rules SET isSelected = 1 WHERE id = :id")
    suspend fun selectRule(id: String)
}
