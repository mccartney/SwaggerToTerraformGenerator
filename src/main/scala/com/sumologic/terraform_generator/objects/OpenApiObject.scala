package com.sumologic.terraform_generator.objects

import org.openapitools.codegen.utils.StringUtils

abstract class OpenApiObject(name: String,
                             objType: OpenApiType,
                             required: Boolean,
                             defaultOpt: Option[AnyRef],
                             description: String,
                             example: String = "",
                             pattern: String = "",
                             format: String = "",
                             attribute: String = "",
                             createOnly: Boolean = false) extends TerraformEntity {

  // TODO Assumption of NO collision without namespaces is probably wrong - should fix
  def getAllTypes: List[OpenApiType] = {
    List(objType) ++ objType.props.flatMap(_.getAllTypes)
  }

  def getName: String = { name }
  def getType: OpenApiType = { objType }
  def getRequired: Boolean = { required }
  def getDescription: String = { description }
  def getExample: String = { example }
  def getCreateOnly: Boolean = { createOnly }
  def getPattern: String = { pattern }
  def getDefault: Option[AnyRef] = { defaultOpt}
  def getFormat: String = { format }
  def getAttribute: String = { attribute }

  def getAsTerraformFunctionArgument: String

  def getGoType: String

  def getTerraformElementSchema: String

  def getAsTerraformSchemaType(forUseInDataResource: Boolean): String = {
    val schemaType = if (this.isInstanceOf[OpenApiArrayObject]) {
      TerraformSchemaTypes.openApiTypeToTerraformSchemaType("array")
    } else {
      TerraformSchemaTypes.openApiTypeToTerraformSchemaType(objType.name)
    }

    val requiredTxt = if (required) { // TODO: check why Frank did it this way
      "Required: true,"
    } else {
      "Optional: true,"
    }

    val specifics = if (forUseInDataResource) {
      "Computed: true,"
    } else {
      defaultOpt match { // This probably won't work for composite types
        case Some(defaultValue) => if (objType.name == "string") {
          s"""ForceNew: false,
             |Default: "${defaultValue.toString}",
             |""".stripMargin
        } else {
          // FIXME Need to handle default values of other type
          ""
        }
        case None => ""
      }
    }

    val validationAndDiffSuppress = if (!this.isInstanceOf[OpenApiArrayObject] && this.getType.props.nonEmpty) {
      """ValidateFunc: validation.StringIsJSON,
        |DiffSuppressFunc: suppressEquivalentJsonDiffs,
        |""".stripMargin
    } else {
      ""
    }

    val elementType = if (this.isInstanceOf[OpenApiArrayObject]) {
      if (this.getType.props.nonEmpty) {
        s"""Elem:  &schema.Schema{
           |  Type: ${TerraformSchemaTypes.openApiTypeToTerraformSchemaType(objType.name)},
           |  ValidateFunc:     validation.StringIsJSON,
           |	DiffSuppressFunc: suppressEquivalentJsonDiffs,
           |},""".stripMargin
      } else {
        s"""Elem:  &schema.Schema{
           |  Type: ${TerraformSchemaTypes.openApiTypeToTerraformSchemaType(objType.name)},
           |},""".stripMargin
      }
    } else {
      ""
    }

    val schemaFieldName = StringUtils.underscore(name)
    s"""
       |"$schemaFieldName": {
       |    Type: $schemaType,
       |    $requiredTxt
       |    $specifics
       |    $validationAndDiffSuppress
       |    $elementType
       |}""".stripMargin
  }
}


case class OpenApiSimpleObject(name: String,
                               objType: OpenApiType,
                               required: Boolean,
                               defaultOpt: Option[AnyRef],
                               description: String,
                               example: String = "",
                               pattern: String = "",
                               format: String = "",
                               attribute: String = "",
                               createOnly: Boolean = false) extends
  OpenApiObject(name: String,
    objType: OpenApiType,
    required: Boolean,
    defaultOpt: Option[AnyRef],
    description,
    example,
    pattern,
    format,
    attribute,
    createOnly) {

  override def terraformify(resource: TerraformResource): String = {
    val req = if (name.toLowerCase != "id") {
      ""
    } else {
      ",omitempty"
    }
    if (name.toLowerCase == "id") {
      s"${name.toUpperCase} ${objType.name} " + "`" + "json:\"" + name + req + "\"" + "`" + "\n"
    } else {
      s"${name.capitalize} ${objType.name} " + "`" + "json:\"" + name + req + "\"" + "`" + "\n"
    }
  }

  override def getGoType: String = {
    TerraformSchemaTypes.openApiTypeToGoType(objType.name)
  }

  override def getTerraformElementSchema: String = {
    val itemType = TerraformSchemaTypes.openApiTypeToTerraformSchemaType(objType.name)
    s"""Elem:  &schema.Schema{
       |    Type: $itemType,
       |},""".stripMargin
  }

  def getAsTerraformFunctionArgument: String = {
    s"$name ${objType.name}"
  }
}


case class OpenApiArrayObject(name: String,
                              objType: OpenApiType,
                              required: Boolean,
                              defaultOpt: Option[AnyRef],
                              description: String,
                              example: String = "",
                              pattern: String = "",
                              format: String = "",
                              attribute: String = "",
                              createOnly: Boolean = false) extends
  OpenApiObject(name: String,
    objType: OpenApiType,
    required: Boolean,
    defaultOpt: Option[AnyRef],
    description,
    example,
    pattern,
    format,
    attribute,
    createOnly) {

  // Captures the type of item contained with in the array object.
  var items: OpenApiObject = _

  override def terraformify(resource: TerraformResource): String = {
    val req = if (required) {
      ""
    } else {
      ",omitempty"
    }

    s"${name.capitalize} $getGoType " + "`" + "json:\"" + name + req + "\"" + "`" + "\n"
  }

  override def getGoType: String = {
    s"[]${items.getGoType}"
  }

  def getAsTerraformFunctionArgument: String = {
    s"$name $getGoType"
  }

  override def getTerraformElementSchema: String = {
    val schemaType = TerraformSchemaTypes.openApiTypeToTerraformSchemaType("array")
    val itemSchema = this.items.getTerraformElementSchema
    s"""Elem:  &schema.Schema{
       |    Type: $schemaType,
       |    $itemSchema
       |},""".stripMargin
  }

  override def getAsTerraformSchemaType(forUseInDataResource: Boolean): String = {
    val schemaType = TerraformSchemaTypes.openApiTypeToTerraformSchemaType("array")

    val requiredTxt = if (required) {
      "Required: true"
    } else {
      "Optional: true"
    }

    val specifics = if (forUseInDataResource) {
      // TODO I am not sure if this is all we need.
      "Computed: true"
    } else {
      // NOTE: Hack to avoid multiline and long descriptions. Need a better solution.
      val idx = this.getDescription.indexOf(". ")
      val end = if (idx != -1) idx else this.getDescription.length
      val description = this.getDescription.substring(0, end).replace('\n', ' ')
      s"""Description: "$description" """
      // TODO add specific for array items like max items, min items
    }

    // TODO Add support for validateFunc and DiffSuppressFunc

    val elementSchema = this.items.getTerraformElementSchema

    val schemaFieldName = StringUtils.underscore(name)
    s"""
       |"$schemaFieldName": {
       |    Type: $schemaType,
       |    $requiredTxt,
       |    $specifics,
       |    $elementSchema
       |}""".stripMargin
  }
}


case class OpenApiRefObject(name: String,
                            objType: OpenApiType,
                            required: Boolean,
                            defaultOpt: Option[AnyRef],
                            description: String,
                            example: String = "",
                            pattern: String = "",
                            format: String = "",
                            attribute: String = "",
                            createOnly: Boolean = false) extends
    OpenApiObject(name: String,
      objType: OpenApiType,
      required: Boolean,
      defaultOpt: Option[AnyRef],
      description,
      example,
      pattern,
      format,
      attribute,
      createOnly) {

  override def getAsTerraformFunctionArgument: String = {
    s"$name $getGoType"
  }

  override def getGoType: String = {
    s"${objType.name.capitalize}"
  }

  override def getTerraformElementSchema: String = {
    val itemSchema = this.getType.props.map { prop =>
      prop.getAsTerraformSchemaType(false)
    }.mkString(",\n").concat(",")

    s"""Elem: &schema.Resource{
       |    Schema: map[string]*schema.Schema{
       |        $itemSchema
       |    },
       |},""".stripMargin
  }

  override def getAsTerraformSchemaType(forUseInDataResource: Boolean): String = {
    val schemaType = TerraformSchemaTypes.openApiTypeToTerraformSchemaType("array")

    val requiredTxt = if (required) {
      "Required: true"
    } else {
      "Optional: true"
    }

    val specifics = if (forUseInDataResource) {
      // TODO I am not sure if this is all we need.
      "Computed: true"
    } else {
      val description = Option(this.getDescription) match {
        case Some(_) =>
          // NOTE: Hack to avoid multiline and long descriptions. Need a better solution.
          val idx = this.getDescription.indexOf(". ")
          val end = if (idx != -1) idx else this.getDescription.length
          this.getDescription.substring(0, end).replace('\n', ' ')
        case None =>
          ""
      }
      s"""|MaxItems: 1,
          |Description: "$description"""".stripMargin
    }

    // TODO Add support for validateFunc and DiffSuppressFunc

    // get schema of referenced type
    val refSchemaType = this.getType.props.map { prop =>
      prop.getAsTerraformSchemaType(forUseInDataResource)
    }.mkString(",\n").concat(",")

    val elementSchema =
      s"""Elem: &schema.Resource{
         |    Schema: map[string]*schema.Schema{
         |        $refSchemaType
         |    },
         |},""".stripMargin

    val schemaFieldName = StringUtils.underscore(name)
    s"""
       |"$schemaFieldName": {
       |    Type: $schemaType,
       |    $requiredTxt,
       |    $specifics,
       |    $elementSchema
       |}""".stripMargin
  }

  override def terraformify(resource: TerraformResource): String = {
    val req = if (required) {
      ""
    } else {
      ",omitempty"
    }

    s"${name.capitalize} $getGoType " + "`" + "json:\"" + name + req + "\"" + "`" + "\n"
  }
}
