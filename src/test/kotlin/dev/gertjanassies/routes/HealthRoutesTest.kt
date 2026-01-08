package dev.gertjanassies.routes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

class HealthRoutesTest : FunSpec({
    context("GET /health") {
        test("should return 200 OK") {
            testApplication {
                routing { healthRoutes() }

                val response = client.get("/health")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe ""
            }
        }
    }
})
