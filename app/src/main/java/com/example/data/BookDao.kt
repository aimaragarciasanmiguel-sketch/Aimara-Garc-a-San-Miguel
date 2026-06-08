package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    // --- Projects ---
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<BookProject>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectByIdFlow(id: Int): Flow<BookProject?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Int): BookProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: BookProject): Long

    @Update
    suspend fun updateProject(project: BookProject)

    @Delete
    suspend fun deleteProject(project: BookProject)

    // --- Pages ---
    @Query("SELECT * FROM pages WHERE projectId = :projectId ORDER BY pageNumber ASC")
    fun getPagesForProjectFlow(projectId: Int): Flow<List<BookPage>>

    @Query("SELECT * FROM pages WHERE projectId = :projectId ORDER BY pageNumber ASC")
    suspend fun getPagesForProject(projectId: Int): List<BookPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: BookPage): Long

    @Update
    suspend fun updatePage(page: BookPage)

    @Delete
    suspend fun deletePage(page: BookPage)

    @Query("DELETE FROM pages WHERE projectId = :projectId")
    suspend fun deletePagesForProject(projectId: Int)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: Int)
}
