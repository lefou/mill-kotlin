package de.tobiasroeser.mill.kotlin

import mill.scalalib.TestModule

/**
 * A [[TestModule]] with support for the Kotlin compiler.
 *
 * @see [[KotlinModule]] for details.
 */
trait KotlinTestModule extends TestModule with KotlinModule {
}
