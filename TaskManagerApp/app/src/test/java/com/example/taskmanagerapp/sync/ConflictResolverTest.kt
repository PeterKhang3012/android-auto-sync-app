package com.example.taskmanagerapp.sync

import com.example.taskmanagerapp.data.local.TaskEntity
import com.example.taskmanagerapp.data.remote.TaskDto
import org.junit.Assert.*
import org.junit.Test

class ConflictResolverTest {
    private val local = TaskEntity(id = "a", title = "Local", updatedAt = 20)
    private val server = TaskDto(id = "a", title = "Server", updatedAt = 10)

    @Test fun lastWriteWinsUsesTimestampAndServerWinsTies() {
        assertTrue(ConflictResolver.clientWins(ConflictPolicy.LAST_WRITE_WINS, local, server))
        assertFalse(ConflictResolver.clientWins(ConflictPolicy.LAST_WRITE_WINS, local, server.copy(updatedAt = 20)))
        assertFalse(ConflictResolver.clientWins(ConflictPolicy.LAST_WRITE_WINS, local, server.copy(updatedAt = 30)))
    }

    @Test fun explicitPoliciesOverrideTimestamps() {
        assertFalse(ConflictResolver.clientWins(ConflictPolicy.SERVER_WINS, local, server))
        assertTrue(ConflictResolver.clientWins(ConflictPolicy.CLIENT_WINS, local, server.copy(updatedAt = 30)))
    }
}
