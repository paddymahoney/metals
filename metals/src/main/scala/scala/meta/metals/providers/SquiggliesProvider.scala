package scala.meta.metals.providers

import scala.meta.metals.Linter
import scala.meta.metals.Configuration
import com.typesafe.scalalogging.LazyLogging
import scala.{meta => m}
import scala.meta.metals.ScalametaEnrichments._
import org.langmeta.lsp.MonixEnrichments._
import org.langmeta.lsp.PublishDiagnostics
import org.langmeta.jsonrpc.JsonRpcClient
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.langmeta.AbsolutePath

class SquiggliesProvider(
    configuration: Observable[Configuration],
    cwd: AbsolutePath
)(implicit s: Scheduler, client: JsonRpcClient)
    extends LazyLogging {
  private val isEnabled: () => Boolean =
    configuration.map(_.scalafix.enabled).toFunction0()

  lazy val linter = new Linter(configuration, cwd)

  def squigglies(doc: m.Document): Task[Seq[PublishDiagnostics]] =
    squigglies(m.Database(doc :: Nil))
  def squigglies(db: m.Database): Task[Seq[PublishDiagnostics]] = {
    if (!isEnabled()) Task(Nil)
    else
      Task.sequence {
        db.documents.map { document =>
          val uri = document.input.syntax
          val compilerErrors = document.messages.map(_.toLSP)
          linter.linterMessages(document).map { scalafixResult =>
            val scalafixErrors = scalafixResult.getOrElse(Nil)
            PublishDiagnostics(uri, compilerErrors ++ scalafixErrors)
          }
        }
      }
  }
}
