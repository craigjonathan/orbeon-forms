/**
*  Copyright (C) 2012 Orbeon, Inc.
*
*  This program is free software; you can redistribute it and/or modify it under the terms of the
*  GNU Lesser General Public License as published by the Free Software Foundation; either version
*  2.1 of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
*  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
*  See the GNU Lesser General Public License for more details.
*
*  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
*/
package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.xml.{TransformerUtils, SAXStore}
import javax.xml.transform.OutputKeys
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource
import javax.xml.transform.stream.StreamResult
import java.io._
import org.orbeon.oxf.xforms.XFormsInstance
import org.orbeon.oxf.util.URLRewriterUtils.PathMatcher
import org.apache.commons.lang.StringUtils
import org.orbeon.oxf.xforms.state.DynamicState.Control

import sbinary._
import sbinary.Operations._
import org.dom4j.{Namespace, QName, Document}

object Operations {

    // NOTE: We use immutable.Seq instead of Array to indicate immutability

    def toByteSeq[T: Writes](t: T): Seq[Byte] =
        toByteArray(t).toSeq

    def fromByteSeq[T: Reads](bytes: Seq[Byte]): T =
        fromByteArray(bytes.toArray) // TODO: inefficient copy to array → implement Input instead
}

object XFormsProtocols extends StandardTypes with StandardPrimitives with JavaLongUTF {

    class JavaOutputStream(output: Output) extends OutputStream {
        def write(b: Int) { output.writeByte(b.asInstanceOf[Byte]) }
        override def write(b: Array[Byte], off: Int, len: Int) { output.writeAll(b, off, len) }
    }

    class JavaInputStream(input: Input) extends InputStream {
        def read() = input.readByte
        override def read(b: Array[Byte], off: Int, len: Int) = input.readTo(b, off, len)
    }

    // Base trait for stuff that should be serialized via Serializable/Externalizable
    trait SerializableFormat[T <: java.io.Serializable] extends Format[T] {

        def writes(output: Output, o: T) =
            new ObjectOutputStream(new JavaOutputStream(output)).writeObject(o)

        def reads(input: Input) =
            new ObjectInputStream(new JavaInputStream(input)).readObject.asInstanceOf[T]
    }

    implicit object DynamicStateFormat extends SerializableFormat[DynamicState]
    implicit object SAXStoreFormat extends SerializableFormat[SAXStore]

    implicit object Dom4jFormat extends Format[Document] {
        def writes(output: Output, document: Document) = {
            val identity = TransformerUtils.getXMLIdentityTransformer
            identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            identity.transform(new LocationDocumentSource(document), new StreamResult(new JavaOutputStream(output)));
        }

        def reads(input: Input) =
            TransformerUtils.readDom4j(new JavaInputStream(input), null, false, false)
    }

    implicit object ControlFormat extends Format[Control] {

        def writes(output: Output, control: Control) = {
            write(output, control.effectiveId)
            write(output, control.keyValues)
        }

        def reads(input: Input) =
            Control(read[String](input), read[Map[String, String]](input))
    }
    
    implicit object InstanceFormat extends Format[XFormsInstance] {
        
        def writes(output: Output, instance: XFormsInstance) {

            write(output, instance.staticId)
            write(output, instance.modelEffectiveId)

            write(output, StringUtils.trimToEmpty(instance.sourceURI))
            write(output, StringUtils.trimToEmpty(instance.username))
            write(output, StringUtils.trimToEmpty(instance.password))
            write(output, StringUtils.trimToEmpty(instance.domain))

            write(output, instance.cache)
            write(output, instance.timeToLive)
            write(output, StringUtils.trimToEmpty(instance.requestBodyHash))

            write(output, StringUtils.trimToEmpty(instance.validation))
            write(output, instance.handleXInclude)
            write(output, instance.exposeXPathTypes)

            write(output, instance.replaced)
            write(output, instance.readonly)

            if (! instance.cache) {
                val xmlString =
                    if (instance.getDocument ne null) {
                        // This is probably more optimal than going through NodeInfo. Furthermore, there may be an issue with
                        // namespaces when using tinyTreeToString(). Bug in the NodeWrapper or dom4j?
                        val dom4jString = TransformerUtils.dom4jToString(instance.getDocument)
                        //System.out.println("dom4jToString: " + instance.modelEffectiveId + "/" + instance.staticId)
                        dom4jString
                    }
                    else {
                        val tinyTreeString = TransformerUtils.tinyTreeToString(instance.documentInfo)
                        //System.out.println("tinyTreeToString: " + instance.modelEffectiveId + "/" + instance.staticId)
                        tinyTreeString
                    }
                //System.out.println("  size: " + xmlString.length)
                write(output, xmlString)
            }
        }
        
        def reads(in: Input) = {

            val staticId = read[String](in)
            val modelEffectiveId = read[String](in)

            val sourceURI = StringUtils.trimToNull(read[String](in))
            val username = StringUtils.trimToNull(read[String](in))
            val password = StringUtils.trimToNull(read[String](in))
            val domain = StringUtils.trimToNull(read[String](in))

            val cache = read[Boolean](in)
            val timeToLive = read[Long](in)
            val requestBodyHash = StringUtils.trimToNull(read[String](in))

            val validation = StringUtils.trimToNull(read[String](in))
            val handleXInclude = read[Boolean](in)
            val exposeXPathTypes = read[Boolean](in)

            val replaced = read[Boolean](in)
            val readonly = read[Boolean](in)

            val documentInfo =
                if (! cache) {
                    val xmlString = read[String](in)
                    XFormsInstance.createDocumentInfo(xmlString, readonly = readonly, exposeXPathTypes = exposeXPathTypes)
                } else
                    null

            new XFormsInstance(
                staticId,
                modelEffectiveId,

                sourceURI,
                username,
                password,
                domain,

                cache,
                timeToLive,
                requestBodyHash,

                readonly,
                validation,
                handleXInclude,
                exposeXPathTypes,
                documentInfo,

                replaced
            )
        }
    }

    implicit object QNameFormat extends Format[QName] {
        def writes(out: Output, value: QName) {
            write(out, value.getName)
            write(out, value.getNamespace.getPrefix)
            write(out, value.getNamespace.getURI)
            write(out, value.getQualifiedName)
            
        }

        def reads(in: Input) =
            QName.get(
                read[String](in),
                Namespace.get(read[String](in), read[String](in)),
                read[String](in)
            )
    }
    
    implicit object PathMatcherFormat extends Format[PathMatcher] {
        def writes(output: Output, value: PathMatcher) {
            write(output, value.pathInfo)
            write(output, Option(value.matcher))
            write(output, Option(value.mimeType))
            write(output, value.versioned)
        }

        def reads(in: Input) =
            new PathMatcher(
                read[String](in),
                read[Option[QName]](in).orNull,
                read[Option[String]](in).orNull,
                read[Boolean](in)
            )
    }
}

// Modified version of sbinary JavaUTF to support reading/writing longer strings
trait JavaLongUTF extends CoreProtocol {
    
    private def getUTFLength(s: String) = {

        val length = s.length
        var result = 0L

        var i = 0
        while (i < length) {
            val c = s.charAt(i)
            result += (
                if ((c >= 0x0001) && (c <= 0x007F)) 1
                else if (c > 0x07FF) 3
                else 2)
            i += 1
        }

        result
    }

    private[this] val buffers = new java.lang.ThreadLocal[(Array[Char], Array[Byte])] {
        override def initialValue() = (new Array[Char](80), new Array[Byte](80))
    }

    private[this] def fetchBuffers(size: Int) = {
        if (buffers.get()._1.length < size)
            buffers.set((new Array[Char](size * 2), new Array[Byte](size * 2)))

        buffers.get()
    }

    implicit object StringFormat extends Format[String] {
        def reads(input: Input) = {
            // Read 4-byte size header (ObjectInputStream uses 2 or 8)
            val utfLength = read[Int](input)
            val (cbuffer, bbuffer) = fetchBuffers(utfLength)

            input.readFully(bbuffer, 0, utfLength)

            var count, charCount, c, char2, char3 = 0

            def malformed(index: Int) = throw new UTFDataFormatException("Malformed input around byte " + index)
            def partial = throw new UTFDataFormatException("Malformed input: Partial character at end")

            while ((count < utfLength) && { c = bbuffer(count) & 0xff; c <= 127 }) {
                cbuffer(charCount) = c.toChar
                charCount += 1
                count += 1
            }

            while (count < utfLength) {
                c = bbuffer(count).toInt & 0xFF
                cbuffer(charCount) = ((c >> 4) match {
                    case 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 ⇒ {
                        count += 1
                        c
                    }
                    case 12 | 13 ⇒ {
                        count += 2
                        if (count > utfLength) partial

                        char2 = bbuffer(count - 1)
                        if ((char2 & 0xC0) != 0x80) malformed(count)
                        (((c & 0x1F) << 6) | (char2 & 0x3F))
                    }
                    case 14 ⇒ {
                        count += 3
                        char2 = bbuffer(count - 2)
                        char3 = bbuffer(count - 1)
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                            malformed(count - 1)

                        ((c & 0x0F).toInt << 12) | ((char2 & 0x3F).toInt << 6) | ((char3 & 0x3F).toInt << 0)
                    }
                    case _ ⇒ malformed(count)
                }).toChar
                charCount += 1
            }

            new String(cbuffer, 0, charCount)
        }

        def writes(output: Output, value: String) {

            // Write 4-byte size header (ObjectOutputStream uses 2 or 8)
            val utfLength = getUTFLength(value).toInt
            write(output, utfLength)

            val bbuffer = fetchBuffers(utfLength)._2
            var count = 0
            def append(value: Int) {
                bbuffer(count) = value.toByte
                count += 1
            }

            var i = 0
            def c = value.charAt(i)

            while ((i < value.length) && ((c >= 0x0001) && (c <= 0x007F))) {
                bbuffer(count) = c.toByte
                count += 1
                i += 1
            }

            while (i < value.length) {
                if ((c >= 0x0001) && (c <= 0x007F)) {
                    append(c)
                } else if (c > 0x07FF) {
                    append(0xE0 | ((c >> 12) & 0x0F))
                    append(0x80 | ((c >> 6) & 0x3F))
                    append(0x80 | ((c >> 0) & 0x3F))
                } else {
                    append(0xC0 | ((c >> 6) & 0x1F))
                    append(0x80 | ((c >> 0) & 0x3F))
                }

                i += 1
            }

            output.writeAll(bbuffer, 0, utfLength)
        }
    }
}