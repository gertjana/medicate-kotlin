package dev.gertjanassies.service

import dev.gertjanassies.model.MedicineSearchResult
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Service for searching medicines using SQLite database.
 * This is memory-efficient as it doesn't load the entire dataset into memory.
 */
object MedicineSearchService {
    private val logger = LoggerFactory.getLogger(MedicineSearchService::class.java)
    private const val DEFAULT_LIMIT = 10

    private val dbPath: String by lazy {
        val dataDir = System.getenv("MEDICINES_DATA_DIR") ?: "data"
        "$dataDir/medicines.db"
    }

    init {
        logger.info("Initializing MedicineSearchService with database: $dbPath")
        ensureDatabase()
    }

    private fun ensureDatabase() {
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            logger.warn("Medicine database not found at: $dbPath")
            logger.warn("Please run the migration script to create the database from medicines.json")
        } else {
            logger.info("Medicine database found at: $dbPath")
        }
    }

    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    /**
     * Search for medicines by name, form, or active ingredients.
     * Uses SQLite FTS5 for efficient full-text search.
     */
    fun searchMedicines(query: String, limit: Int = DEFAULT_LIMIT): List<MedicineSearchResult> {
        val trimmedQuery = query.trim()

        if (trimmedQuery.length < 2) {
            return emptyList()
        }

        // Handle negative or zero limit
        if (limit <= 0) {
            return emptyList()
        }

        val results = mutableListOf<MedicineSearchResult>()

        try {
            getConnection().use { conn ->
                // Use LIKE for simple pattern matching - works with standard SQLite
                val sql = """
                    SELECT productnaam, farmaceutischevorm, werkzamestoffen, bijsluiter_filenaam
                    FROM medicines
                    WHERE productnaam LIKE ?
                       OR farmaceutischevorm LIKE ?
                       OR werkzamestoffen LIKE ?
                    LIMIT ?
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    val searchPattern = "%$trimmedQuery%"
                    stmt.setString(1, searchPattern)
                    stmt.setString(2, searchPattern)
                    stmt.setString(3, searchPattern)
                    stmt.setInt(4, limit)

                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        results.add(
                            MedicineSearchResult(
                                productnaam = rs.getString("productnaam") ?: "",
                                farmaceutischevorm = rs.getString("farmaceutischevorm") ?: "",
                                werkzamestoffen = rs.getString("werkzamestoffen") ?: "",
                                bijsluiter_filenaam = rs.getString("bijsluiter_filenaam") ?: ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to search medicines: ${e.message}", e)
            throw e
        }

        return results
    }
}
