package scraper

import scala.collection.mutable
import scala.reflect.runtime.universe.WeakTypeTag

import scraper.exceptions.TableNotFoundException
import scraper.expressions.Expression
import scraper.parser.Parser
import scraper.plans.logical._
import scraper.plans.physical.PhysicalPlan
import scraper.plans.{Optimizer, QueryPlanner, physical}
import scraper.trees.RulesExecutor
import scraper.types.{LongType, TupleType}

trait Catalog {
  def registerRelation(tableName: String, analyzedPlan: LogicalPlan): Unit

  def lookupRelation(tableName: String): LogicalPlan
}

trait Context {
  type QueryExecution <: plans.QueryExecution

  type Catalog <: scraper.Catalog

  private[scraper] def catalog: Catalog

  private[scraper] def parse(query: String): LogicalPlan

  private[scraper] def analyze: RulesExecutor[LogicalPlan]

  private[scraper] def optimize: RulesExecutor[LogicalPlan]

  private[scraper] def plan: QueryPlanner[LogicalPlan, PhysicalPlan]

  def execute(logicalPlan: LogicalPlan): QueryExecution

  lazy val single: DataFrame = new DataFrame(SingleRowRelation, this)

  def single(first: Expression, rest: Expression*): DataFrame = single select first +: rest

  def q(query: String): DataFrame

  def table(name: String): DataFrame
}

object Context {
  implicit class QueryString(query: String)(implicit context: Context) {
    def q: DataFrame = context q query
  }
}

class LocalContext extends Context {
  type QueryExecution = LocalQueryExecution

  override type Catalog = LocalCatalog

  override private[scraper] val catalog: Catalog = new Catalog

  override private[scraper] def parse(query: String): LogicalPlan = new Parser().parse(query)

  override private[scraper] val analyze = new Analyzer(catalog)

  override private[scraper] val optimize = new Optimizer

  override private[scraper] val plan = new LocalQueryPlanner

  def lift[T <: Product: WeakTypeTag](data: Traversable[T]): DataFrame =
    new DataFrame(new QueryExecution(LocalRelation(data), this))

  def lift[T <: Product: WeakTypeTag](data: Traversable[T], columnNames: String*): DataFrame = {
    val LocalRelation(rows, schema) = LocalRelation(data)
    new DataFrame(LocalRelation(rows, schema rename columnNames), this)
  }

  def range(end: Long): DataFrame = range(0, end)

  def range(begin: Long, end: Long): DataFrame = {
    val rows = (begin until end).map(Row.apply(_))
    val schema = TupleType('id -> LongType.!)
    new DataFrame(LocalRelation(rows, schema), this)
  }

  override def q(query: String): DataFrame = new DataFrame(parse(query), this)

  def execute(logicalPlan: LogicalPlan): QueryExecution = new QueryExecution(logicalPlan, this)

  override def table(name: String): DataFrame = new DataFrame(catalog lookupRelation name, this)
}

class LocalCatalog extends Catalog {
  private val tables: mutable.Map[String, LogicalPlan] = mutable.Map.empty

  override def registerRelation(tableName: String, analyzedPlan: LogicalPlan): Unit =
    tables(tableName) = analyzedPlan

  override def lookupRelation(tableName: String): LogicalPlan =
    tables getOrElse (tableName, throw new TableNotFoundException(tableName))
}

class LocalQueryExecution(val logicalPlan: LogicalPlan, val context: LocalContext)
  extends plans.QueryExecution

class LocalQueryPlanner extends QueryPlanner[LogicalPlan, PhysicalPlan] {
  override def strategies: Seq[Strategy] = Seq(
    BasicOperators
  )

  object BasicOperators extends Strategy {
    override def apply(logicalPlan: LogicalPlan): Seq[PhysicalPlan] = logicalPlan match {
      case child Project projectList =>
        physical.Project(planLater(child), projectList) :: Nil

      case child Filter condition =>
        physical.Filter(planLater(child), condition) :: Nil

      case child Limit n =>
        physical.Limit(planLater(child), n) :: Nil

      case Join(left, right, Inner, None) =>
        physical.CartesianProduct(planLater(left), planLater(right)) :: Nil

      case relation @ LocalRelation(data, _) =>
        physical.LocalRelation(data.toIterator, relation.output) :: Nil

      case child Subquery _ =>
        planLater(child) :: Nil

      case EmptyRelation =>
        physical.EmptyRelation :: Nil

      case SingleRowRelation =>
        physical.SingleRowRelation :: Nil

      case _ => Nil
    }
  }
}
