package com.thedoer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Interface d'accès à la table des surnoms. Room génère
 * automatiquement l'implémentation à la compilation (via KSP) —
 * on ne touche jamais au SQL nous-mêmes au-delà de ces annotations.
 */
@Dao
interface ContactNicknameDao {

    /**
     * Recherche un surnom déjà enregistré. Retourne null si
     * l'utilisateur n'a encore jamais utilisé ce surnom.
     */
    @Query("SELECT * FROM contact_nicknames WHERE nickname = :nickname LIMIT 1")
    suspend fun findByNickname(nickname: String): ContactNickname?

    /**
     * Enregistre un nouveau surnom, ou remplace l'existant si
     * l'utilisateur reformule différemment pour la même personne
     * (ex: il avait dit "maman", il redit "ma mère" -> on garde
     * la dernière résolution en cas de conflit sur le même surnom).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(nickname: ContactNickname)

    @Query("SELECT * FROM contact_nicknames")
    suspend fun getAll(): List<ContactNickname>

    @Query("DELETE FROM contact_nicknames WHERE nickname = :nickname")
    suspend fun delete(nickname: String)
}
