package com.sumologic.terraform_generator.writer

import java.time.{LocalDateTime, ZoneOffset}

import com.sumologic.terraform_generator.StringHelper
import com.sumologic.terraform_generator.objects.{ScalaSwaggerObject, ScalaSwaggerObjectArray}
import nl.flotsam.xeger.Xeger

import scala.util.Random

trait AcceptanceTestGeneratorHelper extends StringHelper {
  def getTestValue(prop: ScalaSwaggerObject, isUpdate: Boolean = false, canUpdate: Boolean = false): String = {
    prop.getType().name match {
      case "bool" =>
        val testBoolValue = if (prop.getDefault().isDefined) {
          prop.getDefault().get.asInstanceOf[Boolean]
        } else {
          false
        }
        testBoolValue.toString
      case "int" =>
        //TODO: Add functionality to update ints
        val testIntValue = if (prop.getExample().nonEmpty) {
          prop.getExample()
        }
        else if (prop.getDefault().isDefined) {
          prop.getDefault().get.toString
        } else {
          "0"
        }

        if (isUpdate && canUpdate) {
          (testIntValue.toLong + 1).toString
        }else {
          testIntValue
        }
      case "[]string" =>
        if (prop.getDefault().isDefined) {
          val default = prop.getDefault().get.asInstanceOf[List[String]]
          s"""[]string{"${default.head.replace("\"", "\\\"")}"}"""
        } else if (prop.getExample().nonEmpty) {
          val items = prop.getExample().replace("[", "").replace("]", "").split(",")
          s"""[]string{"${items.head.replace("\"", "\\\"")}"}"""
        } else if (prop.asInstanceOf[ScalaSwaggerObjectArray].getPattern().nonEmpty) {
          val generator = new Xeger(prop.getPattern())
          val generatedString = generator.generate()
          s"""[]string{"${generatedString.replace("\"", "\\\"")}"}"""
        } else {
          val r = new Random
          val sb = new StringBuilder
          for (i <- 1 to 10) {
            sb.append(r.nextPrintableChar)
          }
          s"""[]string{"${sb.toString().replace("\"", "\\\"")}"}"""
        }
      case "string" =>
        val testStringValue = if (prop.getDefault().isDefined) {
          s""""${prop.getDefault().get.toString.replace(""""""", """\"""")}""""
        } else if (prop.getExample().nonEmpty) {
          s""""${prop.getExample().toString.replace(""""""", """\"""")}""""
        } else if (prop.getPattern().nonEmpty) {
          val generator = new Xeger(prop.getPattern())
          generator.generate()
          s""""${generator.generate().replace(""""""", """\"""")}""""
        } else {
          if (prop.getFormat() == "date-time") {
            s""""${LocalDateTime.now(ZoneOffset.UTC).toString.dropRight(1)}Z""""
          } else {
            val r = new Random
            val sb = new StringBuilder
            for (i <- 1 to 10) {
              sb.append(r.nextPrintableChar)
            }
            s"""${sb.toString.replace(""""""", """\"""")}"""
          }
        }

        if (isUpdate && canUpdate) {
          testStringValue.dropRight(1) + """Update""""
        } else {
          testStringValue
        }
      case _ =>
        throw new RuntimeException("Trying to generate test values for an unsupported type.")
    }
  }
}
