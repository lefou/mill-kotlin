import $file.plugins
import $file.shared

import mill._
import mill.scalalib._
import de.tobiasroeser.mill.kotlin._
import org.scalatest.Assertions

// Adapted from source: https://github.com/Kotlin/kotlin-examples/tree/master/maven/mixed-code-hello-world
object main extends KotlinModule {

  def kotlinVersion = "1.2.0"

  def mainClass = Some("hello.JavaHello")

  object test extends Tests {
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    def ivyDeps = Agg(
      ivy"com.novocode:junit-interface:0.11",
      ivy"junit:junit:4.12",
      ivy"org.jetbrains.kotlin:kotlin-test-junit:${kotlinVersion()}"
    )
  }

  def kotlincOptions = Seq("-verbose")

}

def verify(): Command[Unit] = T.command {
  val A = new Assertions {}
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
