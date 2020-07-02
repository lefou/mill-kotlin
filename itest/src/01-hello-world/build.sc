import $exec.plugins
import $ivy.`org.scoverage::scalac-scoverage-runtime:1.4.1`

import mill._
import mill.scalalib._
import mill.define._

import de.tobiasroeser.mill.kotlin._

import $ivy.`org.scalatest::scalatest:3.2.0`
import org.scalatest.Assertions

// Adapted from source: https://github.com/Kotlin/kotlin-examples/tree/master/maven/hello-world
object main extends KotlinModule {

  def kotlinVersion = T{ "1.0.3" }

  def ivyDeps = T{ Agg(
    ivy"org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion()}"
  )}

  def mainClass = Some("hello.HelloKt")

  object test extends Tests {
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    def ivyDeps = Agg(
      ivy"com.novocode:junit-interface:0.11",
      ivy"junit:junit:4.12",
      ivy"org.jetbrains.kotlin:kotlin-test-junit:${kotlinVersion()}"
    )
  }

}

def verify(): Command[Unit] = T.command {
  val A = new Assertions{}
  import A._

  val cr = main.compile()
  val classFiles = os.walk(cr.classes.path).filter(os.isFile)
  assert(classFiles.isEmpty === false)

  main.run()()

  val tcr = main.test.compile()
  val testClassFiles = os.walk(tcr.classes.path).filter(os.isFile)
  assert(testClassFiles.isEmpty === false)

  val (_, tr) = main.test.test()()
  assert(tr.size === 1)
  assert(tr.head.status === "Success")
  ()
}
