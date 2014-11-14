package models

import com.gilt.apidocgenerator.models.Container
import core.Text._
import core.generator.ScalaModel

case class Play2Json(serviceName: String) {

  def generate(model: ScalaModel): String = {
    readers(model) + "\n\n" + writers(model)
  }

  def readers(model: ScalaModel): String = {
    Seq(
      s"implicit def jsonReads${serviceName}${model.name}: play.api.libs.json.Reads[${model.name}] = {",
      fieldReaders(model).indent(2),
      s"}"
    ).mkString("\n")
  }

  private def fieldReaders(model: ScalaModel): String = {
    val serializations = model.fields.map { field =>
      field.`type`.container match {
        case Container.Singleton => {
          if (field.isOption) {
            s"""(__ \\ "${field.originalName}").readNullable[${field.datatype.name}]"""
          } else {
            s"""(__ \\ "${field.originalName}").read[${field.datatype.name}]"""
          }
        }

        case Container.List => {
          s"""(__ \\ "${field.originalName}").readNullable[${field.datatype.name}].map(_.getOrElse(Nil))"""
        }

        case Container.Map => {
          s"""(__ \\ "${field.originalName}").readNullable[${field.datatype.name}].map(_.getOrElse(Map.Empty))"""
        }

        case Container.UNDEFINED(container) => {
          sys.error(s"Invalid container[$container]")
        }
      }
    }

    model.fields match {
      case field :: Nil => {
        serializations.head + s""".map { x => new ${model.name}(${field.name} = x) }"""
      }
      case fields => {
        Seq(
          "(",
          serializations.mkString(" and\n").indent(2),
          s")(${model.name}.apply _)"
        ).mkString("\n")
      }
    }
  }

  def writers(model: ScalaModel): String = {
    model.fields match {
      case field :: Nil => {
        Seq(
          s"implicit def jsonWrites${serviceName}${model.name}: play.api.libs.json.Writes[${model.name}] = new play.api.libs.json.Writes[${model.name}] {",
          s"  def writes(x: ${model.name}) = play.api.libs.json.Json.obj(",
          s"""    "${field.originalName}" -> play.api.libs.json.Json.toJson(x.${field.name})""",
          "  )",
          "}"
        ).mkString("\n")
      }

      case fields => {
        Seq(
          s"implicit def jsonWrites${serviceName}${model.name}: play.api.libs.json.Writes[${model.name}] = {",
          s"  (",
          model.fields.map { field =>
            if (field.isOption) {
              s"""(__ \\ "${field.originalName}").write[scala.Option[${field.datatype.name}]]"""
            } else {
              s"""(__ \\ "${field.originalName}").write[${field.datatype.name}]"""
            }
          }.mkString(" and\n").indent(4),
          s"  )(unlift(${model.name}.unapply _))",
          s"}"
        ).mkString("\n")
      }
    }
  }

}
