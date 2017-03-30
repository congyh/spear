package scraper.plans.logical.analysis

import scraper.expressions._
import scraper.plans.logical.{LocalRelation, LogicalPlan}

abstract class WindowAnalysisTest extends AnalyzerTest { self =>
  override protected def afterAll(): Unit = catalog.removeRelation('t)

  protected val relation: LogicalPlan = {
    catalog.registerRelation('t, LocalRelation.empty('a.int.!, 'b.string.?))
    catalog.lookupRelation('t)
  }

  protected val Seq(a: AttributeRef, b: AttributeRef) = relation.output
}
