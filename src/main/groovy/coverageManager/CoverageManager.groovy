package coverageManager

import groovy.util.logging.Slf4j
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

@Slf4j
class CoverageManager {

    String xmlFile
    List coveredMethods

    CoverageManager(String folder){
        this.xmlFile = "${folder}${File.separator}coverage.xml"
        this.coveredMethods = []
    }

    private void removeDoctype(){
        def input = new File(xmlFile).text
        def lines = input.readLines()
        def index = lines.findIndexOf{
            it.contains('<!DOCTYPE coverage SYSTEM \"http://cobertura.sourceforge.net/xml/coverage-04.dtd\">')
        }
        if(index>=0) {
            lines.remove(index)
            def newText = ""
            lines.each{
                newText += it+"\n"
            }
            def newFile = new File(xmlFile)
            newFile.write(newText)
        }
    }

    void configureCoveredMethods(){
        def file = new File(xmlFile)
        if(!file.exists()){
            log.error "File ${xmlFile} was not found."
            return
        }

        coveredMethods = []
        removeDoctype()
        def input = file.text
        def doc = DOMBuilder.parse(new StringReader(input), false, false)
        NodeList lineNodes = doc.getElementsByTagName("line")

        use(DOMCategory) {
            List<Node> coveredLines = lineNodes.findAll { line ->
                line.attributes.getNamedItem("hits").text() != "0"
            }

            coveredLines.each { cl ->
                def method = cl.parentNode.parentNode.attributes.getNamedItem("name").text()
                def className = cl.parentNode.parentNode.parentNode.parentNode.attributes.getNamedItem("name").text()
                if(!method.contains(".")) coveredMethods += ["${className}::${method}"]
            }
            coveredMethods = coveredMethods.unique()
        }
    }

}
