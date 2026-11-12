package com.speechtotext.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts ORDER BY isPinned DESC, createdAt DESC")
    fun getAllTranscripts(): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    suspend fun getTranscriptById(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    fun observeTranscriptById(id: Long): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcripts WHERE title LIKE '%' || :query || '%' OR fullText LIKE '%' || :query || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchTranscripts(query: String): Flow<List<TranscriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: TranscriptEntity): Long

    @Update
    suspend fun update(transcript: TranscriptEntity)

    @Query("UPDATE transcripts SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE transcripts SET fullText = :fullText, wordsJson = :wordsJson WHERE id = :id")
    suspend fun updateContent(id: Long, fullText: String, wordsJson: String)

    @Query("UPDATE transcripts SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean)

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transcripts")
    suspend fun deleteAll()
}
