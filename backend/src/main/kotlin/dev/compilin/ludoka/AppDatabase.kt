package dev.compilin.ludoka

import dev.compilin.ludoka.model.UserService
import io.ktor.server.application.*
import io.ktor.util.logging.*
import org.jetbrains.exposed.sql.*


class AppDatabase(environment: ApplicationEnvironment, log: Logger) {

    val database = Database.connect(
        driver = environment.config.property("ludoka.db.driver").getString(),
        url = environment.config.property("ludoka.db.url").getString(),
        user = environment.config.property("ludoka.db.user").getString(),
        password = environment.config.property("ludoka.db.password").getString()
    )

    val users = UserService(database, log)
}