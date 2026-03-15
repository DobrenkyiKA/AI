package com.kdob.piq.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication
class AiApplication

fun main(args: Array<String>) {
    runApplication<AiApplication>(*args)
}
