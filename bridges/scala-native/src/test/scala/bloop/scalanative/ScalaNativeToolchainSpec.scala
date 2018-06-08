package bloop.scalanative

import bloop.{DependencyResolution, Project, ScalaInstance}
import bloop.cli.{Commands, OptimizerConfig}
import bloop.config.Config
import bloop.engine.{Run, State}
import bloop.exec.JavaEnv
import bloop.io.AbsolutePath
import bloop.logging.{Logger, RecordingLogger}
import bloop.tasks.TestUtil

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(Array(classOf[bloop.FastTests]))
class ScalaNativeToolchainSpec {

  @Test
  def canLinkScalaNativeProject(): Unit = {
    val logger = new RecordingLogger
    val state = TestUtil
      .loadTestProject("cross-platform", _.map(setScalaNativeClasspath))
      .copy(logger = logger)
    val action = Run(Commands.Link(project = "crossNative"))
    val resultingState = TestUtil.blockingExecute(action, state, maxDuration)

    assertTrue(s"Linking failed: ${logger.getMessages.mkString("\n")}", resultingState.status.isOk)
    logger.getMessages.assertContain("Scala Native binary:", atLevel = "info")
  }

  @Test
  def canLinkScalaNativeProjectInReleaseMode(): Unit = {
    val logger = new RecordingLogger
    val state = TestUtil
      .loadTestProject("cross-platform", _.map(setScalaNativeClasspath))
      .copy(logger = logger)
    val action = Run(Commands.Link(project = "crossNative", optimize = OptimizerConfig.Release))
    val resultingState = TestUtil.blockingExecute(action, state, maxDuration * 2)

    assertTrue(s"Linking failed: ${logger.getMessages.mkString("\n")}", resultingState.status.isOk)
    logger.getMessages.assertContain("Optimizing (release mode)", atLevel = "info")
  }

  @Test
  def canRunScalaNativeProject(): Unit = {
    val logger = new RecordingLogger
    val state = TestUtil
      .loadTestProject("cross-platform", _.map(setScalaNativeClasspath))
      .copy(logger = logger)
    val action = Run(Commands.Run(project = "crossNative"))
    val resultingState = TestUtil.blockingExecute(action, state, maxDuration)

    assertTrue(s"Run failed: ${logger.getMessages.mkString("\n")}", resultingState.status.isOk)
    logger.getMessages.assertContain("Hello, world!", atLevel = "info")
  }

  private val maxDuration = Duration.apply(30, TimeUnit.SECONDS)

  // Set a dummy `nativeClasspath` for the Scala Native toolchain.
  // This is to avoid trying to resolve the toolchain with Coursier,
  // and will work because the toolchain is on this module's classpath.
  private val setScalaNativeClasspath: Project => Project = {
    case prj if prj.platform == Config.Platform.Native =>
      prj.copy(nativeConfig = Some(NativeBridge.defaultNativeConfig(prj)))
    case other =>
      other
  }

  private implicit class RichLogs(logs: List[(String, String)]) {
    def assertContain(needle: String, atLevel: String): Unit = {
      def failMessage = s"""Logs didn't contain `$needle` at level `$atLevel`. Logs were:
                           |${logs.mkString("\n")}""".stripMargin
      assertTrue(failMessage, logs.exists {
        case (`atLevel`, msg) => msg.contains(needle)
        case _ => false
      })
    }
  }
}
