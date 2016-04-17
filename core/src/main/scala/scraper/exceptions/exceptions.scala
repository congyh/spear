package scraper.exceptions

import scraper.expressions.{AttributeRef, Expression}
import scraper.plans.logical.LogicalPlan
import scraper.types.{AbstractDataType, DataType}
import scraper.utils._

class ParsingException(message: String) extends RuntimeException(message)

class BrokenContractException(message: String, cause: Throwable)
  extends RuntimeException(message, cause) {

  def this(message: String) = this(message, null)
}

abstract class AnalysisException(message: String, cause: Throwable)
  extends RuntimeException(message, cause)

class ExpressionUnevaluableException(expression: Expression, cause: Throwable)
  extends AnalysisException(s"Expression ${expression.debugString} is unevaluable", cause) {

  def this(expression: Expression) = this(expression, null)
}

class ExpressionUnresolvedException(expression: Expression, cause: Throwable)
  extends AnalysisException(s"Expression ${expression.nodeCaption} is unresolved", cause) {

  def this(expression: Expression) = this(expression, null)
}

class LogicalPlanUnresolvedException(plan: LogicalPlan, cause: Throwable)
  extends AnalysisException(s"Unresolved logical query plan:\n\n${plan.prettyTree}", cause) {

  def this(plan: LogicalPlan) = this(plan, null)
}

class TypeCheckException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(expression: Expression, cause: Throwable) =
    this({
      val causeMessage = Option(cause) map (". " + _.getMessage) getOrElse ""
      s"""Expression ${expression.debugString} doesn't pass type check$causeMessage:
         |${expression.prettyTree}
         |""".stripMargin
    }, cause)

  def this(expression: Expression) = this(expression, null)

  def this(plan: LogicalPlan, cause: Throwable) =
    this({
      s"""Logical query plan doesn't pass type check:
         |${plan.prettyTree}
         |""".stripMargin
    }, cause)

  def this(plan: LogicalPlan) = this(plan, null)
}

class TypeCastException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(from: DataType, to: DataType, cause: Throwable) =
    this(s"Cannot convert data type $from to $to", cause)

  def this(from: DataType, to: DataType) = this(from, to, null)
}

class ImplicitCastException(message: String, cause: Throwable)
  extends TypeCastException(message, cause) {

  def this(from: Expression, to: DataType, cause: Throwable) = this({
    s"""Cannot convert expression [${from.debugString}]
       |of data type ${from.dataType} to $to implicitly.
     """.straight
  }, cause)

  def this(from: Expression, to: DataType) = this(from, to, null)
}

class TypeMismatchException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(message: String) = this(message, null)

  def this(
    expression: Expression,
    expected: DataType,
    cause: Throwable
  ) = this(
    s"Expecting ${expected.sql}, while expression $expression has type ${expression.dataType.sql}",
    cause
  )

  def this(
    expression: Expression,
    expected: AbstractDataType,
    cause: Throwable
  ) = this(
    s"Expecting a $expected, while expression $expression has type ${expression.dataType.sql}",
    cause
  )

  def this(expression: Expression, expected: DataType) = this(expression, expected, null)

  def this(expression: Expression, expected: AbstractDataType) = this(expression, expected, null)
}

class ResolutionFailureException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(message: String) = this(message, null)
}

class TableNotFoundException(tableName: String, cause: Throwable)
  extends AnalysisException(s"Table $tableName not found", cause) {

  def this(tableName: String) = this(tableName, null)
}

class FunctionNotFoundException(name: String, cause: Throwable)
  extends AnalysisException(s"Function $name not found", cause) {

  def this(name: String) = this(name, null)
}

class SchemaIncompatibleException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(message: String) = this(message, null)
}

class IllegalAggregationException(message: String, cause: Throwable)
  extends AnalysisException(message, cause) {

  def this(
    part: String,
    attribute: AttributeRef,
    expression: Expression,
    keys: Seq[Expression],
    cause: Throwable
  ) = this({
    val ks = keys map (_.debugString) mkString ("[", ", ", "]")
    s"""$part ${expression.debugString} references attribute ${attribute.debugString},
       |which is neither an aggregate function nor a grouping key among $ks
     """.straight
  }, cause)

  def this(part: String, attribute: AttributeRef, expression: Expression, keys: Seq[Expression]) =
    this(part, attribute, expression, keys, null)
}

class SettingsValidationException(message: String, cause: Throwable)
  extends RuntimeException(message, cause) {

  def this(message: String) = this(message, null)
}
