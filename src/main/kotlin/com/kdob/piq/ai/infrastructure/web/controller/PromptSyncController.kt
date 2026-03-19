package com.kdob.piq.ai.infrastructure.web.controller

import com.kdob.piq.ai.application.service.PromptSyncService
import com.kdob.piq.ai.infrastructure.client.storage.StorageServiceClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/prompts/sync")
class PromptSyncController(
    private val promptSyncService: PromptSyncService,
    private val storageClient: StorageServiceClient
) {

    @GetMapping("/versions")
    fun getVersions(): List<String> {
        storageClient.refresh()
        return storageClient.getVersions()
    }

    @PostMapping("/export")
    fun export(@RequestParam version: String, @RequestParam message: String) {
        promptSyncService.exportToVersion(version, message)
    }

    @PostMapping("/import")
    fun import(@RequestParam version: String) {
        storageClient.refresh()
        promptSyncService.importFromVersion(version)
    }

    @DeleteMapping("/versions/{version}")
    fun deleteVersion(@PathVariable version: String) {
        storageClient.deleteVersion(version)
    }

    @GetMapping("/versions/{version}/last-commit-message")
    fun getLastCommitMessage(@PathVariable version: String): String {
        return storageClient.getLastCommitMessage(version)
    }
}