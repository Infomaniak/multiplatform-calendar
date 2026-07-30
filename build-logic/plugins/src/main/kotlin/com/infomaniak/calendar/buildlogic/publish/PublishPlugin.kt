/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.calendar.buildlogic.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class PublishPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        plugins.apply(SigningPlugin::class.java)
        plugins.apply("maven-publish")
        plugins.apply("com.gradleup.nmcp")

        group = "com.infomaniak.multiplatform_calendar"
        version = getPropertyValue("core.version") ?: "unspecified"

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    withType<MavenPublication> {
                        pom {
                            name.set("MultiplatformCalendar")
                            description.set("Multiplatform Calendar - MultiplatformCalendar core library")
                            licenses {
                                license {
                                    name.set("GPL-3.0")
                                    url.set("https://www.gnu.org/licenses/gpl-3.0.fr.html")
                                }
                            }
                            url.set("https://github.com/Infomaniak/multiplatform-calendar")
                            issueManagement {
                                system.set("Github")
                                url.set("https://github.com/Infomaniak/multiplatform-calendar/issues")
                            }
                            scm {
                                connection.set("https://github.com/Infomaniak/multiplatform-calendar.git")
                                url.set("https://github.com/Infomaniak/multiplatform-calendar")
                            }
                            organization {
                                name.set("Infomaniak Network SA")
                                url.set("https://www.infomaniak.com/")
                            }
                            developers {
                                developer {
                                    id.set("Infomaniak")
                                    email.set("mobile+libraries@infomaniak-dev.ch")
                                    name.set("Infomaniak Development Team")
                                    url.set("https://www.infomaniak.com/")
                                }
                            }
                        }
                    }
                }

                // Reposilite: public read (repos "releases"/"snapshots" are configured as
                // PUBLIC visibility server-side), authenticated write only. Credentials come
                // from gradle.properties (local dev, gitignored) or env vars (CI secrets) —
                // never hardcoded. If they're missing, Gradle will simply fail the `publish`
                // task for THIS repository when actually invoked, without blocking the
                // Maven Central (nmcp) publication path.
                repositories {
                    maven {
                        name = "reposilite"
                        url = uri(
                            if (version.toString().endsWith("SNAPSHOT")) {
                                "https://maven.infomaniak.app/snapshots"
                            } else if (version.toString().endsWith("dev")) {
                                "https://maven.infomaniak.app/dev"
                            } else {
                                "https://maven.infomaniak.app/releases"
                            }
                        )
                        credentials {
                            username = getPropertyValue("reposiliteUsername")
                            password = getPropertyValue("reposilitePassword")
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                val keyId: String = getPropertyValue("GPG_key_id") ?: return@configure
                val ringFile: String = getPropertyValue("GPG_private_key")?.replace('#', '\n') ?: return@configure
                val password: String = getPropertyValue("GPG_private_password") ?: return@configure

                isRequired = true
                useInMemoryPgpKeys(keyId, ringFile, password)
                sign(project.extensions.getByType<PublishingExtension>().publications)

                // Workaround for a Gradle bug, the issue is still open.
                // https://github.com/gradle/gradle/issues/26091#issuecomment-1722947958
                tasks.withType<AbstractPublishToMaven>().configureEach {
                    val signingTasks = tasks.withType<Sign>()
                    mustRunAfter(signingTasks)
                }
            }
        }
    }

    private fun Project.getPropertyValue(propertyName: String): String? {
        if (project.hasProperty(propertyName)) return project.property(propertyName) as String
        return System.getenv(propertyName)
    }

}
