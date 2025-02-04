package org.modelix.model.sync.bulk.lib.test

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

fun xmlToString(doc: Document): String {
    val transformerFactory: TransformerFactory = TransformerFactory.newInstance()
    val transformer: Transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    val source = DOMSource(doc)
    val out = StringWriter()
    val result = StreamResult(out)
    transformer.transform(source, result)
    return out.toString()
}

fun readXmlFile(file: File): Document {
    try {
        val dbf = DocumentBuilderFactory.newInstance()
        // dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        disableDTD(dbf)
        val db = dbf.newDocumentBuilder()
        return db.parse(file)
    } catch (e: Exception) {
        throw RuntimeException("Failed to read ${file.absoluteFile}", e)
    }
}

fun readXmlFile(file: InputStream, name: String? = null): Document {
    val dbf = DocumentBuilderFactory.newInstance()
    // dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    disableDTD(dbf)
    val db = dbf.newDocumentBuilder()
    return db.parse(file, name)
}

private fun disableDTD(dbf: DocumentBuilderFactory) {
    dbf.setValidating(false)
    dbf.setNamespaceAware(true)
    dbf.setFeature("http://xml.org/sax/features/namespaces", false)
    dbf.setFeature("http://xml.org/sax/features/validation", false)
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
}

fun Node.visitAll(visitor: (Node) -> Unit) {
    visitor(this)
    val childNodes = this.childNodes
    for (i in 0 until childNodes.length) childNodes.item(i).visitAll(visitor)
}

fun Node.childElements(): List<Element> = children().filterIsInstance<Element>()
fun Node.childElements(tag: String): List<Element> = children().filterIsInstance<Element>().filter { it.tagName == tag }
fun Node.children(): List<Node> {
    val children = childNodes
    val result = ArrayList<Node>(children.length)
    for (i in 0 until children.length) result += children.item(i)
    return result
}
